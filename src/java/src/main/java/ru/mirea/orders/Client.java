package ru.mirea.orders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class Client {
    private final long clientId;
    private final String firstName;
    private final String middleName;
    private final String lastName;
    private final List<Contact> contacts = new ArrayList<>();

    Client(long clientId, String firstName, String middleName, String lastName) {
        this.clientId = clientId;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
    }

    void addContact(Contact contact) {
        contacts.add(contact);
    }

    boolean hasFirstName() {
        return !Contact.trim(firstName).isEmpty();
    }

    boolean hasLastName() {
        return !Contact.trim(lastName).isEmpty();
    }

    public Optional<Contact> preferredContact() {
        return contacts.stream().filter(Contact::isPresent).findFirst();
    }
}
