package ui.components.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import models.Customer
import ui.*
import ui.components.GlassCard
import ui.components.LoadingCard

@Composable
fun CustomerListSection(
    customers: List<Customer>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCustomerClick: (Customer) -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        elevation = AppElevation.sm
    ) {
        // Header với title và search field
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DANH SÁCH KHÁCH HÀNG",
                fontSize = AppTypography.h5size,
                fontWeight = FontWeight.Bold,
                color = AppColors.Primary
            )
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.width(200.dp),
                placeholder = { Text("Tìm kiếm...", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Tìm kiếm",
                        tint = AppColors.TextSecondary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Xóa",
                                modifier = Modifier.size(20.dp),
                                tint = AppColors.TextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = AppColors.Primary,
                    unfocusedBorderColor = AppColors.Border,
                    backgroundColor = AppColors.Surface
                ),
                shape = RoundedCornerShape(AppRadius.md)
            )
        }
        Divider(
            modifier = Modifier.padding(vertical = AppSpacing.md),
            color = AppColors.Divider
        )
        // Loading state với shimmer animation
        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                repeat(3) {
                    LoadingCard()
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        } else if (customers.isEmpty()) {
            // Empty state với animation
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(800)) +
                        expandVertically(animationSpec = tween(600))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Chưa có khách hàng",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                    }
                }
            }
        } else {
            // Filter customers theo search query
            val filteredCustomers = remember(customers, searchQuery) {
                if (searchQuery.isBlank()) {
                    customers
                } else {
                    val query = searchQuery.lowercase().trim()
                    customers.filter { customer ->
                        customer.fullName.lowercase().contains(query) ||
                        customer.cardId.lowercase().contains(query) ||
                        customer.cccd.lowercase().contains(query)
                    }
                }
            }
            // Customer list với staggered animation
            if (filteredCustomers.isEmpty() && searchQuery.isNotEmpty()) {
                // Empty search result
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = AppColors.TextSecondary.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Không tìm thấy khách hàng",
                            fontSize = AppTypography.bodyLargeSize,
                            color = AppColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    filteredCustomers.forEachIndexed { index, customer ->
                        AnimatedCustomerListItem(
                            customer = customer,
                            index = index,
                            onClick = { onCustomerClick(customer) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
