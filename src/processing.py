from copy import deepcopy

from algorithms import calculate_total, check_request
from validation import parse_time, positive_integer


def next_id(rows, field):
    """Получить следующий свободный числовой ключ."""
    return max((row[field] for row in rows), default=0) + 1


def prepare_request(data, request):
    """Выбрать связанные записи и нормализовать данные до вызова алгоритма."""
    normalized = dict(request)
    for field in ('client_id', 'delivery_address_id'):
        if not positive_integer(normalized.get(field)):
            normalized[field] = None
    client = next((row for row in data['clients']
                   if row['client_id'] == normalized['client_id']), {})
    address = next((row for row in data['addresses']
                    if row['address_id'] == normalized['delivery_address_id']), {})
    client = {key: value.strip() if isinstance(value, str) else value
              for key, value in client.items()}
    address = {key: value.strip() if isinstance(value, str) else value
               for key, value in address.items()}
    contacts = [row['email'] for row in data['emails']
                if row['client_id'] == normalized['client_id']]
    contacts += [row['phone_number'] for row in data['phone_numbers']
                 if row['client_id'] == normalized['client_id']]
    contact = next((value.strip() for value in contacts
                    if isinstance(value, str) and value.strip()), '')
    items = [dict(row) for row in data['request_items']
             if row['request_id'] == request['request_id']]
    for item in items:
        if not positive_integer(item.get('product_id')):
            item['product_id'] = None
    return normalized, client, contact, address, items


def check_stock(items, products, stocks):
    """Проверить каталог и суммарную потребность в каждом товаре."""
    known = {row['product_id'] for row in products}
    available = {row['product_id']: row['available_quantity'] for row in stocks}
    required = {}
    for item in items:
        if not positive_integer(item.get('product_id')) or not positive_integer(item.get('quantity')):
            raise ValueError('До проверки остатков необходимо проверить состав заявки.')
        product_id = item['product_id']
        required[product_id] = required.get(product_id, 0) + item['quantity']
    results = []
    for item in items:
        product_id = item['product_id']
        if product_id not in known:
            passed, message = False, f'Товар {product_id} отсутствует в каталоге.'
        elif product_id not in available:
            passed, message = False, f'Нет данных об остатке товара {product_id}.'
        elif available[product_id] < required[product_id]:
            passed = False
            message = (f'Товар {product_id}: требуется {required[product_id]}, '
                       f'доступно {available[product_id]}.')
        else:
            passed, message = True, f'Товар {product_id} доступен в нужном количестве.'
        results.append({'request_item_id': item['request_item_id'],
                        'is_passed': passed, 'message': message})
    return bool(items) and all(row['is_passed'] for row in results), results


def record_check(data, code, passed, message, at, request_id=None, order_item_id=None):
    """Добавить результат с одной ссылкой: на заявку либо позицию заказа."""
    if (request_id is None) == (order_item_id is None):
        raise ValueError('Результату проверки нужна ровно одна ссылка.')
    check_type = next(row for row in data['check_types'] if row['code'] == code)
    data['check_results'].append({
        'check_result_id': next_id(data['check_results'], 'check_result_id'),
        'request_id': request_id, 'check_type_id': check_type['check_type_id'],
        'order_item_id': order_item_id, 'is_passed': passed,
        'message': message, 'checked_at': at,
    })


def reserve_order(data, order_id, at):
    """Вернуть копию данных с полным резервом; при отказе ничего не менять."""
    updated = deepcopy(data)
    order = next((row for row in updated['orders'] if row['order_id'] == order_id), None)
    if order is None:
        raise ValueError(f'Заказ {order_id} не найден.')
    items = [row for row in updated['order_items'] if row['order_id'] == order_id]
    sources = {row['request_item_id']: row for row in updated['request_items']}
    request_items = [sources[row['request_item_id']] for row in items]
    reserved = {row['order_item_id']: row for row in updated['reservations']}
    existing = [reserved[row['order_item_id']] for row in items
                if row['order_item_id'] in reserved]
    if existing:
        complete = len(existing) == len(items) and all(
            reserved.get(item['order_item_id'], {}).get('reserved_quantity')
            == sources[item['request_item_id']]['quantity'] for item in items)
        if complete and order['status'] == 'confirmed':
            return updated, True, 'Резерв уже существует.'
        return updated, False, 'Обнаружен неполный или несогласованный резерв.'
    if order['status'] != 'processing' or not items or any(row['price'] is None for row in items):
        return updated, False, 'Заказ не подготовлен к резервированию.'
    passed, checks = check_stock(request_items, updated['products'], updated['stocks'])
    if not passed:
        return updated, False, ' '.join(row['message'] for row in checks if not row['is_passed'])
    stocks = {row['product_id']: row for row in updated['stocks']}
    for item in items:
        source = sources[item['request_item_id']]
        stock = stocks[source['product_id']]
        stock['available_quantity'] -= source['quantity']
        stock['updated_at'] = at
        updated['reservations'].append({
            'reservation_id': next_id(updated['reservations'], 'reservation_id'),
            'order_item_id': item['order_item_id'],
            'reserved_quantity': source['quantity'], 'reserved_at': at,
        })
    return updated, True, 'Весь состав заказа зарезервирован.'


def describe_errors(errors):
    """Преобразовать коды первого алгоритма в сообщение для пользователя."""
    messages = []
    for name, position in errors:
        if name == 'empty':
            messages.append('Состав заявки пуст.')
        elif name == 'item':
            messages.append(f'Позиция {position}: нужен product_id и целое количество больше нуля.')
        else:
            messages.append(f'Не заполнено поле {name}.')
    return ' '.join(messages)


def request_result(data, request_id):
    """Собрать итог из сохраненных статусов, цен и истории проверок."""
    request = next((row for row in data['requests'] if row['request_id'] == request_id), None)
    if request is None:
        raise ValueError(f'Заявка {request_id} не найдена.')
    order = next((row for row in data['orders'] if row['request_id'] == request_id), None)
    items = [row for row in data['order_items']
             if order is not None and row['order_id'] == order['order_id']]
    item_ids = {row['order_item_id'] for row in items}
    sources = {row['request_item_id']: row for row in data['request_items']}
    failed = [row['message'] for row in data['check_results']
              if not row['is_passed'] and (row['request_id'] == request_id
                                          or row['order_item_id'] in item_ids)]
    messages = {
        'new': 'Заявка ожидает обработки.',
        'needs_clarification': failed[-1] if failed else 'Требуется уточнение данных.',
        'awaiting_price': failed[-1] if failed else 'Требуется уточнение прайс-листа.',
        'confirmed': 'Весь состав заказа зарезервирован.',
        'rejected': failed[-1] if failed else 'Подтверждение отклонено.',
    }
    total = None
    if request['status'] == 'confirmed':
        total = sum(item['price'] * sources[item['request_item_id']]['quantity']
                    for item in items)
    return {'request_id': request_id, 'status': request['status'],
            'order_id': order['order_id'] if order else None,
            'total': total, 'message': messages[request['status']]}


def process_request(data, request_id, at):
    """Пройти четыре операции и вернуть новые данные без изменения входных."""
    if not positive_integer(request_id):
        raise ValueError('Номер заявки должен быть положительным целым числом.')
    moment = parse_time(at)
    at = moment.isoformat()
    updated = deepcopy(data)
    request = next((row for row in updated['requests'] if row['request_id'] == request_id), None)
    if request is None:
        raise ValueError(f'Заявка {request_id} не найдена.')
    if request['status'] in ('confirmed', 'rejected'):
        return updated, request_result(updated, request_id)
    order = next((row for row in updated['orders'] if row['request_id'] == request_id), None)
    prepared = prepare_request(updated, request)
    passed, errors = check_request(*prepared)
    message = 'Данные заявки заполнены.' if passed else describe_errors(errors)
    record_check(updated, 'request_data', passed, message, at, request_id=request_id)
    if not passed:
        request['status'] = 'needs_clarification'
        if order is not None:
            order['status'] = 'needs_clarification'
            order['updated_at'] = at
        return updated, request_result(updated, request_id)
    items = prepared[-1]
    if order is None:
        order = {'order_id': next_id(updated['orders'], 'order_id'), 'request_id': request_id,
                 'status': 'processing', 'created_at': at, 'updated_at': at}
        updated['orders'].append(order)
        for item in items:
            updated['order_items'].append({
                'order_item_id': next_id(updated['order_items'], 'order_item_id'),
                'order_id': order['order_id'],
                'request_item_id': item['request_item_id'], 'price': None,
            })
    else:
        order['status'] = 'processing'
        order['updated_at'] = at
    order_id = order['order_id']
    order_items = [row for row in updated['order_items'] if row['order_id'] == order_id]
    by_request_item = {row['request_item_id']: row for row in order_items}
    price_error = False
    passed, checks = check_stock(items, updated['products'], updated['stocks'])
    for check in checks:
        record_check(updated, 'stock', check['is_passed'], check['message'], at,
                     order_item_id=by_request_item[check['request_item_id']]['order_item_id'])
    if passed:
        prices = [dict(row, valid_from=parse_time(row['valid_from']),
                       valid_to=parse_time(row['valid_to']) if row['valid_to'] else None)
                  for row in updated['prices']]
        total, fixed = calculate_total(items, prices, moment)
        if total is None:
            passed = False
            price_error = True
            message = ('Резервирование не выполнено: для одной или нескольких позиций '
                       'нет единственной действующей цены.')
        else:
            for request_item_id, price in fixed:
                by_request_item[request_item_id]['price'] = price
            updated, passed, message = reserve_order(updated, order_id, at)
        for item in order_items:
            record_check(updated, 'reservation', passed, message, at,
                         order_item_id=item['order_item_id'])
    status = 'confirmed' if passed else ('awaiting_price' if price_error else 'rejected')
    order = next(row for row in updated['orders'] if row['order_id'] == order_id)
    order['status'] = status
    request = next(row for row in updated['requests'] if row['request_id'] == request_id)
    request['status'] = status
    return updated, request_result(updated, request_id)
