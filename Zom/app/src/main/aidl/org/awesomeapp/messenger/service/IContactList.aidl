package org.awesomeapp.messenger.service;

import org.awesomeapp.messenger.model.Contact;

interface IContactList {
    /**
     * Gets the name of the list.
     */
    String getName();

    /**
     * Sets the name of the list.
     */
    void setName(String name);

    /**
     * Adds a new contact to the list.
     */
    int addContact(String address, String nickname);

    /**
     * Removes a contact in the list.
     */
    int removeContact(String address);

    /**
     * Sets the list to the default list.
     */
    void setDefault(boolean isDefault);

    /**
     * Tells if the list is the default list.
     */
    boolean isDefault();
}
