package com.rafaam11.businfo.data.credential

interface CredentialStore {
    fun read(): String?
    fun write(serviceKey: String)
    fun clear()
}
