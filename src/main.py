"""Консольная программа проверки и подтверждения заказов электроники."""

import argparse
from datetime import datetime, timezone
from pathlib import Path
import sys

from processing import request_result
from storage import load_data, process_file
from validation import parse_time


def format_money(kopecks):
    """Показать целое число копеек как рубли без вычислений с float."""
    return f'{kopecks // 100:,}'.replace(',', ' ') + f',{kopecks % 100:02d} руб.'


def print_result(result):
    """Вывести номер, статус, сумму и причину принятого решения."""
    names = {'new': 'Ожидает обработки', 'needs_clarification': 'Требует уточнения',
             'awaiting_price': 'Ожидает уточнения цены',
             'confirmed': 'Подтвержден', 'rejected': 'Отклонен'}
    print(f'Заявка: {result["request_id"]}')
    print(f'Статус: {names[result["status"]]}')
    if result['order_id'] is not None:
        print(f'Заказ: {result["order_id"]}')
    if result['total'] is not None:
        print(f'Сумма: {format_money(result["total"])}')
    print(result['message'])


def main(argv=None):
    """Обработать заявку или показать сохраненные данные из командной строки."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data', type=Path,
                        default=Path(__file__).resolve().parent.parent / 'storage' / 'data.json',
                        help='путь к JSON (по умолчанию storage/data.json рядом с src)')
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument('--request-id', type=int, help='обработать заявку с данным номером')
    actions.add_argument('--show', type=int, metavar='REQUEST_ID', help='показать результат заявки')
    actions.add_argument('--list', action='store_true', help='показать заявки без изменения данных')
    parser.add_argument('--at', help='момент расчета в ISO 8601 с часовым поясом')
    args = parser.parse_args(argv)
    if args.at is not None and args.request_id is None:
        parser.error('--at используется только вместе с --request-id')
    try:
        if args.request_id is not None:
            at = parse_time(args.at) if args.at else datetime.now(timezone.utc)
            result = process_file(args.data, args.request_id, at.isoformat())
            print_result(result)
        elif args.show is not None:
            print_result(request_result(load_data(args.data), args.show))
        else:
            data = load_data(args.data)
            for request in data['requests']:
                print(f'{request["request_id"]}: {request["status"]}')
            if not data['requests']:
                print('Заявок нет.')
    except (OSError, ValueError) as error:
        print(f'Ошибка: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
