package de.rpicloud.ipv64net.models

import android.content.Context
import de.rpicloud.ipv64net.helper.PreferencesManager
import java.util.UUID

data class User(
    var id: UUID = UUID.randomUUID(),
    var Username: String = "",
    var ApiKey: String,
    var Information: String
) {
    companion object {
        private lateinit var appContext: Context

        fun init(context: Context) {
            appContext = context.applicationContext
            val apiKey = PreferencesManager.loadString(appContext, "APIKEY")
            if (apiKey.isNotEmpty() && list.isEmpty()) {
                val user = User(Username = "Default User", ApiKey = apiKey, Information = "")
                val list = mutableListOf<User>()
                list.add(user)
                saveList(list)
            }
        }

        val empty = User(Username = "", ApiKey = "", Information = "")

        val current: User?
            get() {
                if (list.isEmpty()) {
                    return null
                }

                val user = list.firstOrNull { it.ApiKey ==  PreferencesManager.loadString(appContext, "APIKEY") }

                if (user != null && user.Information.isEmpty()) {
                    user.Information = "No Information"
                }
                return user
            }

        val list: MutableList<User>
            get() {
                val data = PreferencesManager.loadList<User>(ctx = appContext, key = "APP_USERS")
                var userList = mutableListOf<User>()

                try {
                    if (data.isNotEmpty()) {
                        userList = data

                        var isNilUUID = false

                        userList = userList.map { u ->
                            if (u.id == null) {
                                isNilUUID = true
                                u.copy(id = UUID.randomUUID())
                            } else u
                        }.toMutableList()

                        if (isNilUUID) {
                            saveList(userList)
                        }
                    }
                } catch (e: Exception) {
                    println("Error decoding data: ${e.message}")
                }

                return userList
            }

        fun saveList(list: MutableList<User>) {
            PreferencesManager.saveList(ctx = appContext, key = "APP_USERS", list)
        }
    }

    fun save() {
        val userList = list
        userList.add(this)
        saveList(userList)
    }

    fun update() {
        val userList = list
        val index = userList.indexOfFirst { it.id == this.id }
        if (index != -1) {
            userList[index] = this
            saveList(userList)
        }
    }

    fun delete() {
        val userList = list
        userList.removeAll { it.ApiKey == current?.ApiKey }

        saveList(userList)
        PreferencesManager.saveString(appContext, "APIKEY", userList.first().ApiKey)
    }
}