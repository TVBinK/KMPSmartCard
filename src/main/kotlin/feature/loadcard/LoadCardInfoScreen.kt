package feature.loadcard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import feature.loadcard.components.CheckCardStepContent
import feature.loadcard.components.ConnectStep
import feature.loadcard.components.InputInfoStepContent
import feature.loadcard.components.WriteDataStepContent
import kotlinx.coroutines.launch
import core.model.Customer

/**
 * Màn hình Nạp thông tin vào thẻ Smart Card
 * Sử dụng MVVM pattern
 */
@Composable
fun LoadCardInfoDialog(
    onDismiss: () -> Unit,
    onSuccess: (Customer) -> Unit
) {
    // Khởi tạo ViewModel
    val loadCardInfoViewModel = remember {
        LoadCardInfoViewModel(
            onSuccess = onSuccess,
            onDismiss = onDismiss
        )
    }
    val state by loadCardInfoViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            loadCardInfoViewModel.onCleared()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Title Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nạp thông tin vào thẻ Smart Card",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng")
                    }
                }
                // Progress
                LinearProgressIndicator(
                    progress = when (state.currentStep) {
                        LoadStep.CONNECT -> 0.25f
                        LoadStep.CHECK_CARD -> 0.50f
                        LoadStep.INPUT_INFO -> 0.75f
                        LoadStep.WRITE_DATA -> 1f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Content Section with fixed height and scroll
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
                ) {
                    if (state.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Đang xử lý...", color = Color.Gray)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            when (state.currentStep) {
                                LoadStep.CONNECT -> ConnectStep(
                                    isConnected = state.isConnected,
                                    onConnect = {
                                        coroutineScope.launch {
                                            loadCardInfoViewModel.connect()
                                        }
                                    },
                                    onNext = { loadCardInfoViewModel.nextStep() }
                                )

                                LoadStep.CHECK_CARD -> CheckCardStepContent(
                                    onCheck = {
                                        coroutineScope.launch {
                                            loadCardInfoViewModel.checkCard()
                                        }
                                    },
                                    onClearCard = {
                                        coroutineScope.launch {
                                            loadCardInfoViewModel.clearCard()
                                        }
                                    },
                                    isCardEmpty = state.isCardEmpty
                                )

                                LoadStep.INPUT_INFO -> InputInfoStepContent(
                                    existingCustomers = state.existingCustomers,
                                    useExistingData = state.useExistingData,
                                    onUseExistingDataChange = { loadCardInfoViewModel.setUseExistingData(it) },
                                    selectedExistingCustomer = state.selectedExistingCustomer,
                                    onSelectExistingCustomer = { customer ->
                                        loadCardInfoViewModel.selectExistingCustomer(customer)
                                    },
                                    cardId = state.cardId,
                                    onCardIdChange = { loadCardInfoViewModel.updateCardId(it) },
                                    fullName = state.fullName,
                                    onFullNameChange = { loadCardInfoViewModel.updateFullName(it) },
                                    cccd = state.cccd,
                                    onCccdChange = { loadCardInfoViewModel.updateCccd(it) },
                                    dob = state.dob.text,
                                    onDobChange = { loadCardInfoViewModel.updateDob(it) },
                                    address = state.address,
                                    onAddressChange = { loadCardInfoViewModel.updateAddress(it) },
                                    phone = state.phone,
                                    onPhoneChange = { loadCardInfoViewModel.updatePhone(it) },
                                    cardType = state.cardType,
                                    onCardTypeChange = { loadCardInfoViewModel.updateCardType(it) },
                                    expiryDate = state.expiryDate,
                                    onExpiryDateChange = { loadCardInfoViewModel.updateExpiryDate(it) },
                                    balance = state.balance,
                                    onBalanceChange = { loadCardInfoViewModel.updateBalance(it) },
                                    pin = state.pin,
                                    onPinChange = { loadCardInfoViewModel.updatePin(it) },
                                    photoBytes = state.photoBytes,
                                    onPhotoChange = { loadCardInfoViewModel.updatePhoto(it) },
                                    onPhotoFileSelected = { file -> loadCardInfoViewModel.processImageFile(file) },
                                    photoSizeLimitBytes = MAX_PHOTO_SIZE_BYTES,
                                    onNext = { loadCardInfoViewModel.validateAndNext() }
                                )

                                LoadStep.WRITE_DATA -> WriteDataStepContent(
                                    cardId = state.cardId,
                                    fullName = state.fullName,
                                    cccd = state.cccd,
                                    dob = state.dob.text,
                                    address = state.address,
                                    phone = state.phone,
                                    cardType = state.cardType,
                                    balance = state.balance,
                                    photoSizeBytes = state.photoBytes?.size ?: 0,
                                    photoSizeLimitBytes = MAX_PHOTO_SIZE_BYTES,
                                    isPhotoTooLarge = state.isPhotoTooLarge,
                                    onWrite = {
                                        coroutineScope.launch {
                                            loadCardInfoViewModel.writeDataToCard()
                                        }

                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // Buttons Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Hiển thị nút Quay lại nếu không phải bước đầu tiên và không loading
                    val showBackButton = !state.isLoading && when (state.currentStep) {
                        LoadStep.CONNECT -> false
                        LoadStep.CHECK_CARD -> false
                        LoadStep.INPUT_INFO -> true
                        LoadStep.WRITE_DATA -> true
                    }

                    if (showBackButton) {
                        Button(
                            onClick = { loadCardInfoViewModel.previousStep() },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF757575))
                        ) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Quay lại")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Quay lại", color = Color.White)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF5252))
                    ) {
                        Text("Đóng", color = Color.White)
                    }
                }
            }
        }
    }
}
