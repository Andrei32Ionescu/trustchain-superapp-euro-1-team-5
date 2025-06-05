package nl.tudelft.trustchain.offlineeuro.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import it.unisa.dia.gas.jpbc.Element
import nl.tudelft.offlineeuro.sqldelight.Database
import nl.tudelft.offlineeuro.sqldelight.RegisteredUsersQueries
import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
import nl.tudelft.trustchain.offlineeuro.entity.RegisteredUser

/**
 * An overlay for the *RegisteredUsers* table.
 *
 * This class can be used to interact with the database.
 *
 * @property Context the context of the application, can be null, if a driver is given.
 * @property driver the driver of the database, can be used to pass a custom driver
 */
class RegisteredUserManager(
    context: Context?,
    private val bilinearGroup: BilinearGroup,
    private val driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "registered_users.db"),
) {
    private val database: Database = Database(driver)
    private val queries: RegisteredUsersQueries = database.registeredUsersQueries

    private val registeredUserMapper = {
            id: Long,
            name: String,
            publicKey: ByteArray,
            legalName: String
        ->
        RegisteredUser(
            id,
            name,
            bilinearGroup.pairing.g1.newElementFromBytes(publicKey).immutable,
            legalName
        )
    }

    /**
     * Creates the RegisteredUser table if it not yet exists.
     */
    init {
        queries.createRegisteredUserTable()
        queries.clearAllRegisteredUsers()
    }

    /**
     * Tries to add a new [RegisteredUser] to the table.
     *
     * @param user the user that should be registered. Its id will be omitted.
     * @return true iff registering the user is successful.
     */
    fun addRegisteredUser(
        userName: String,
        publicKey: Element,
        legalName: String
    ): Boolean {
        queries.addUser(
            userName,
            publicKey.toBytes(),
            legalName,
        )
        return true
    }

    /**
     * Gets a [RegisteredUser] by its [name].
     *
     * @param name the name of the [RegisteredUser]
     * @return the [RegisteredUser] with the [name] or null if the user does not exist.
     */
    fun getRegisteredUserByName(name: String): RegisteredUser? {
        return queries.getUserByName(name, registeredUserMapper)
            .executeAsOneOrNull()
    }

    /**
     * Gets a [RegisteredUser] by its [publicKey].
     *
     * @param publicKey the public key of the [RegisteredUser]
     * @return the [RegisteredUser] with the [publicKey] or null if the user does not exist.
     */
    fun getRegisteredUserByPublicKey(publicKey: Element): RegisteredUser? {
        return queries.getUserByPublicKey(publicKey.toBytes(), registeredUserMapper)
            .executeAsOneOrNull()
    }

    fun getAllRegisteredUsers(): List<RegisteredUser> {
        return queries.getAllRegisteredUsers(registeredUserMapper).executeAsList()
    }

    /**
     * Clears all the [RegisteredUser]s from the table.
     */
    fun clearAllRegisteredUsers() {
        queries.clearAllRegisteredUsers()
    }
}
