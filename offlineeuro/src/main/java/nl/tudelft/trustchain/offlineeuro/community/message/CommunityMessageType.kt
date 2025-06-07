package nl.tudelft.trustchain.offlineeuro.community.message

enum class CommunityMessageType {
    AddressMessage,
    AddressRequestMessage,

    RegistrationMessage,

    GroupDescriptionCRSRequestMessage,
    GroupDescriptionCRSReplyMessage,

    TTPRegistrationMessage,

    BlindSignatureRandomnessRequestMessage,
    BlindSignatureRandomnessReplyMessage,

    BlindSignatureRequestMessage,
    BlindSignatureReplyMessage,

    TransactionRandomnessRequestMessage,
    TransactionRandomnessReplyMessage,

    TransactionMessage,
    TransactionResultMessage,

    FraudControlRequestMessage,
    FraudControlReplyMessage,

    EudiInitiateVerificationMessage, // user sends verification request to TTP
    EudiInitiateVerificationReplyMessage, // TTP sends txId, reqURI, reqURIMethod, clientId to User
    EudiVerificationSubmittedMessage, // User sends txId to TTP
    EudiVerificationCompletedMessage, // TTP sends back success/failure to User
}
