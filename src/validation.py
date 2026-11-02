from datetime import datetime, timezone


def parse_time(value):
    """Прочитать ISO 8601 с часовым поясом и привести время к UTC."""
    if not isinstance(value, str):
        raise ValueError('Дата должна быть строкой ISO 8601 с часовым поясом.')
    try:
        result = datetime.fromisoformat(value)
    except ValueError as error:
        raise ValueError(f'Некорректная дата: {value!r}.') from error
    if result.tzinfo is None or result.utcoffset() is None:
        raise ValueError(f'Не указан часовой пояс: {value!r}.')
    return result.astimezone(timezone.utc)


def positive_integer(value):
    """Отличить положительный целый идентификатор от bool и других типов."""
    return type(value) is int and value > 0


def require(condition, message):
    """Остановить обработку некорректного хранилища с понятной причиной."""
    if not condition:
        raise ValueError(message)


def validate_data(data):
    """Проверить 14 сущностей, ключи, ссылки и согласованность результатов."""
    fields = {
        'clients': ('client_id', 'first_name', 'middle_name', 'last_name'),
        'emails': ('email_id', 'client_id', 'email'),
        'phone_numbers': ('phone_id', 'client_id', 'phone_number'),
        'addresses': ('address_id', 'country', 'city', 'street', 'house'),
        'requests': ('request_id', 'client_id', 'received_at',
                     'delivery_address_id', 'status'),
        'request_items': ('request_item_id', 'request_id', 'product_id', 'quantity'),
        'products': ('product_id', 'name', 'category'),
        'prices': ('price_id', 'product_id', 'price_value', 'valid_from', 'valid_to'),
        'stocks': ('product_id', 'available_quantity', 'updated_at'),
        'orders': ('order_id', 'request_id', 'status', 'created_at', 'updated_at'),
        'order_items': ('order_item_id', 'order_id', 'request_item_id', 'price'),
        'reservations': ('reservation_id', 'order_item_id', 'reserved_quantity',
                         'reserved_at'),
        'check_types': ('check_type_id', 'code', 'name'),
        'check_results': ('check_result_id', 'request_id', 'check_type_id',
                          'order_item_id', 'is_passed', 'message', 'checked_at'),
    }
    require(isinstance(data, dict) and set(data) == set(fields),
            'Хранилище должно содержать ровно 14 сущностей логической модели.')
    indexes = {}
    for table, names in fields.items():
        rows = data[table]
        require(isinstance(rows, list), f'{table}: ожидается список записей.')
        index = {}
        for row in rows:
            require(isinstance(row, dict) and set(row) == set(names),
                    f'{table}: неверный набор атрибутов; ожидаются {", ".join(names)}.')
            key = row[names[0]]
            require(positive_integer(key), f'{table}: неверный ключ {key!r}.')
            require(key not in index, f'{table}: повтор ключа {key}.')
            index[key] = row
        indexes[table] = index
    references = (
        ('emails', 'client_id', 'clients'),
        ('phone_numbers', 'client_id', 'clients'),
        ('request_items', 'request_id', 'requests'),
        ('prices', 'product_id', 'products'),
        ('stocks', 'product_id', 'products'),
        ('orders', 'request_id', 'requests'),
        ('order_items', 'order_id', 'orders'),
        ('order_items', 'request_item_id', 'request_items'),
        ('reservations', 'order_item_id', 'order_items'),
        ('check_results', 'check_type_id', 'check_types'),
    )
    for table, field, target in references:
        for row in data[table]:
            key = row[field]
            require(positive_integer(key) and key in indexes[target],
                    f'{table}.{field}: отсутствует запись {key!r} в {target}.')
    for table, field in (('orders', 'request_id'),
                         ('order_items', 'request_item_id'),
                         ('reservations', 'order_item_id'),
                         ('check_types', 'code')):
        values = [row[field] for row in data[table]]
        require(all(isinstance(value, (str, int)) for value in values),
                f'{table}.{field}: неверный тип значения.')
        require(len(values) == len(set(values)), f'{table}.{field}: повтор значения.')
    codes = {row['code'] for row in data['check_types']}
    require({'request_data', 'stock', 'reservation'} <= codes,
            'В check_types нужны коды request_data, stock, reservation.')
    text_fields = {
        'clients': ('first_name', 'middle_name', 'last_name'),
        'emails': ('email',), 'phone_numbers': ('phone_number',),
        'addresses': ('country', 'city', 'street', 'house'),
        'products': ('name', 'category'), 'check_types': ('code', 'name'),
        'check_results': ('message',),
    }
    for table, names in text_fields.items():
        for row in data[table]:
            for name in names:
                require(row[name] is None or isinstance(row[name], str),
                        f'{table}.{name}: ожидается строка или null.')
    for table, names in (
        ('requests', ('received_at',)), ('prices', ('valid_from',)),
        ('stocks', ('updated_at',)), ('orders', ('created_at', 'updated_at')),
        ('reservations', ('reserved_at',)), ('check_results', ('checked_at',)),
    ):
        for row in data[table]:
            for name in names:
                parse_time(row[name])
    for price in data['prices']:
        require(type(price['price_value']) is int and price['price_value'] >= 0,
                'prices.price_value: нужны целые неотрицательные копейки.')
        if price['valid_to'] is not None:
            require(parse_time(price['valid_from']) < parse_time(price['valid_to']),
                    'prices: конец периода должен быть позже начала.')
    for stock in data['stocks']:
        require(type(stock['available_quantity']) is int
                and stock['available_quantity'] >= 0,
                'stocks.available_quantity: нужен целый неотрицательный остаток.')
    for result in data['check_results']:
        targets = (('request_id', 'requests'), ('order_item_id', 'order_items'))
        require(sum(result[field] is not None for field, _ in targets) == 1,
                'check_results: заполните ровно одну ссылку на заявку или позицию.')
        for field, target in targets:
            key = result[field]
            if key is not None:
                require(positive_integer(key) and key in indexes[target],
                        f'check_results.{field}: неверная ссылка.')
        require(type(result['is_passed']) is bool,
                'check_results.is_passed: ожидается true или false.')
    for request in data['requests']:
        require(request['status'] in ('new', 'needs_clarification',
                                      'awaiting_price', 'confirmed', 'rejected'),
                'requests.status: неизвестный статус.')
    for item in data['order_items']:
        order = indexes['orders'][item['order_id']]
        source = indexes['request_items'][item['request_item_id']]
        require(source['request_id'] == order['request_id'],
                'order_items: позиция принадлежит другой заявке.')
        require(item['price'] is None or (type(item['price']) is int
                                         and item['price'] >= 0),
                'order_items.price: ожидаются целые копейки или null.')
    for reservation in data['reservations']:
        item = indexes['order_items'][reservation['order_item_id']]
        source = indexes['request_items'][item['request_item_id']]
        require(positive_integer(reservation['reserved_quantity'])
                and reservation['reserved_quantity'] == source['quantity'],
                'reservations: количество не совпадает с позицией заявки.')
        require(indexes['orders'][item['order_id']]['status'] == 'confirmed',
                'reservations: резерв допустим только для подтвержденного заказа.')
    for order in data['orders']:
        require(order['status'] in ('needs_clarification', 'awaiting_price', 'confirmed', 'rejected'),
                'orders.status: неизвестный статус.')
        request = indexes['requests'][order['request_id']]
        require(order['status'] == request['status'],
                'Статусы заявки и заказа должны совпадать.')
        source_ids = {row['request_item_id'] for row in data['request_items']
                      if row['request_id'] == order['request_id']}
        items = [row for row in data['order_items'] if row['order_id'] == order['order_id']]
        require(source_ids and source_ids == {row['request_item_id'] for row in items},
                'orders: состав заказа не совпадает с заявкой.')
        if order['status'] == 'confirmed':
            reserved = {row['order_item_id'] for row in data['reservations']}
            for item in items:
                source = indexes['request_items'][item['request_item_id']]
                require(item['price'] is not None and item['order_item_id'] in reserved
                        and positive_integer(source['quantity'])
                        and positive_integer(source['product_id'])
                        and source['product_id'] in indexes['products'],
                        'orders: подтвержденный заказ должен иметь цены и полный резерв.')
    for request in data['requests']:
        if request['status'] in ('awaiting_price', 'confirmed', 'rejected'):
            require(any(order['request_id'] == request['request_id']
                        for order in data['orders']),
                    'Окончательный статус заявки требует существующего заказа.')
