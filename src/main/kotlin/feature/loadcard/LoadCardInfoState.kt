package feature.loadcard

import androidx.compose.ui.text.input.TextFieldValue
import core.model.CardType
import core.model.Customer
import java.time.LocalDate

/**
 * Enum các bước trong quá trình nạp thẻ
 */
enum class LoadStep {
    CONNECT,
    CHECK_CARD,
    INPUT_INFO,
    WRITE_DATA
}

/**
 * State của LoadCardInfoScreen
 */
data class LoadCardInfoState(
    val currentStep: LoadStep = LoadStep.CONNECT,
    val isConnected: Boolean = false,
    val isCardEmpty: Boolean = false,
    val statusMessage: String = "Chưa kết nối với thẻ",
    val isLoading: Boolean = false,
    val existingCustomers: List<Customer> = emptyList(),
    val selectedExistingCustomer: Customer? = null,
    val useExistingData: Boolean = false,
    // Input fields
    val cardId: String = "",
    val fullName: String = "",
    val cccd: String = "",
    val dob: TextFieldValue = TextFieldValue(""),
    val address: String = "",
    val phone: String = "",
    val cardType: CardType = CardType.NORMAL,
    val expiryDate: LocalDate = LocalDate.now().plusMonths(1),
    val balance: String = "100000",
    val pin: String = "",
    val photoBytes: ByteArray? = null
) {
    val isPhotoTooLarge: Boolean
        get() = (photoBytes?.size ?: 0) > MAX_PHOTO_SIZE_BYTES
}

const val MAX_PHOTO_SIZE_BYTES = 32767

