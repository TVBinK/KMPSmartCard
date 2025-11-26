package feature.ticketvalidation

import core.model.Customer

/**
 * State của TicketValidationDialog
 */
data class TicketValidationState(
    val customer: Customer,
    val statusMessage: String = "",
    val isLoading: Boolean = false
)

