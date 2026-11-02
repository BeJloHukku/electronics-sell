from contextlib import contextmanager
import fcntl
import json
import os
from pathlib import Path
import tempfile

from processing import process_request
from validation import validate_data


def load_data(path):
    """Загрузить и проверить хранилище, не изменяя файл."""
    with Path(path).open(encoding='utf-8') as stream:
        data = json.load(stream)
    validate_data(data)
    return data


def save_data(path, data):
    """Заменить единый JSON только после полной записи и проверки данных."""
    validate_data(data)
    path = Path(path)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8',
                                         dir=path.parent, prefix=f'.{path.name}.',
                                         suffix='.tmp', delete=False) as stream:
            temporary = Path(stream.name)
            json.dump(data, stream, ensure_ascii=False, indent=2, allow_nan=False)
            stream.write('\n')
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


@contextmanager
def lock_storage(path):
    """Исключить одновременную обработку одного файла двумя процессами."""
    lock_path = Path(path).with_suffix(Path(path).suffix + '.lock')
    with lock_path.open('a', encoding='utf-8') as stream:
        try:
            fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ValueError('Хранилище уже обрабатывает другой процесс.') from error
        try:
            yield
        finally:
            fcntl.flock(stream.fileno(), fcntl.LOCK_UN)


def process_file(path, request_id, at):
    """Прочитать, обработать и сохранить заявку под общей блокировкой."""
    with lock_storage(path):
        data = load_data(path)
        updated, result = process_request(data, request_id, at)
        if updated != data:
            save_data(path, updated)
        return result
