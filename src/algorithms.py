def check_request(request, client, contact, address, items):
    """Вернуть признак полноты и все ошибки: (поле, номер позиции)."""
    errors = []
    required = (
        ('client_id', request.get('client_id')),
        ('first_name', client.get('first_name')),
        ('last_name', client.get('last_name')),
        ('contact', contact),
        ('delivery_address_id', request.get('delivery_address_id')),
        ('country', address.get('country')),
        ('city', address.get('city')),
        ('street', address.get('street')),
        ('house', address.get('house')))
    for name, value in required:
        if not value:
            errors.append((name, 0))
    if not items:
        errors.append(('empty', 0))
    for i, item in enumerate(items, 1):
        q = item.get('quantity')
        if (not item.get('product_id')
                or type(q) is not int or q <= 0):
            errors.append(('item', i))
    return not errors, errors


def calculate_total(items, prices, at):
    """Рассчитать сумму в копейках и цены строк на момент at за O(n*m)."""
    total = 0
    fixed = []
    for item in items:
        matches = 0
        unit = 0
        for p in prices:
            if (p['product_id'] == item['product_id']
                    and p['valid_from'] <= at
                    and (p['valid_to'] is None
                         or at < p['valid_to'])):
                matches += 1
                unit = p['price_value']
        if matches != 1:
            return None, []
        total += item['quantity'] * unit
        fixed.append((item['request_item_id'], unit))
    return total, fixed
