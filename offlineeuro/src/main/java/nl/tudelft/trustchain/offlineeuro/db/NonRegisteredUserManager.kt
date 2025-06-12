package nl.tudelft.trustchain.offlineeuro.db
//
//import android.content.Context
//import app.cash.sqldelight.db.SqlDriver
//import app.cash.sqldelight.driver.android.AndroidSqliteDriver
//import it.unisa.dia.gas.jpbc.Element
//import nl.tudelft.offlineeuro.sqldelight.Database
//import nl.tudelft.offlineeuro.sqldelight.RegisteredUsersQueries
//import nl.tudelft.trustchain.offlineeuro.cryptography.BilinearGroup
//import nl.tudelft.trustchain.offlineeuro.entity.NonRegisteredUser
//
///**
// * An overlay for the *RegisteredUsers* table.
// *
// * This class can be used to interact with the database.
// *
// * @property Context the context of the application, can be null, if a driver is given.
// * @property driver the driver of the database, can be used to pass a custom driver
// */
//class NonRegisteredUserManager(
//    context: Context?,
//    private val bilinearGroup: BilinearGroup,
//    private val driver: SqlDriver = AndroidSqliteDriver(Database.Schema, context!!, "non_registered_users.db"),
//) {
//    private val database: Database = Database(driver)
//    private val queries: RegisteredUsersQueries = database.registeredUsersQueries
//
//    private val NonRegisteredUserMapper = {
//            id: Long,
//            name: String,
//            publicKey: ByteArray,
//            transactionId: String
//        ->
//        NonRegisteredUser(
//            id,
//            name,
//            bilinearGroup.pairing.g1.newElementFromBytes(publicKey).immutable,
//            transactionId
//        )
//    }
//
//    /**
//     * Creates the RegisteredUser table if it not yet exists.
//     */
//    init {
//        queries.createNonRegisteredUserTable()
//        queries.clearAllNonRegisteredUsers()
//    }
//
//    /**
//     * Tries to add a new [NonRegisteredUser] to the table.
//     *
//     * @param user the user that should be registered. Its id will be omitted.
//     * @return true iff registering the user is successful.
//     */
//    fun addRegisteredUser(
//        userName: String,
//        publicKey: Element,
//        legalName: String
//    ): Boolean {
//        queries.addUser(
//            userName,
//            publicKey.toBytes(),
//            legalName,
//        )
//        return true
//    }

//    fun addNonRegisteredUser(
//        userName: String,
//        publicKey: Element,
//        transactionId: String
//    ): Boolean {
//        queries.addNonRegisteredUser(
//            userName,
//            publicKey.toBytes(),
//            transactionId,
//        )
//        return true
//    }

//    /**
//     * Gets a [NonRegisteredUser] by its [name].
//     *
//     * @param name the name of the [NonRegisteredUser]
//     * @return the [NonRegisteredUser] with the [name] or null if the user does not exist.
//     */
//    fun getNonRegisteredUserByTransactionId(transactionId: String): NonRegisteredUser? {
//        return queries.getUserByTransactionId(transactionId, registeredUserMapper)
//            .executeAsOneOrNull()
//    }
//
//    /**
//     * Clears all the [NonRegisteredUser]s from the table.
//     */
//    fun clearAllNonRegisteredUsers() {
//        queries.clearAllNonRegisteredUsers()
//    }
//}
