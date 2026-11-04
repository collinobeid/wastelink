package com.waste.link.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.waste.link.ui.navigation.BnbTabs
import com.waste.link.ui.theme.DiamondGradientFrom
import com.waste.link.ui.theme.DiamondGradientTo
import com.waste.link.ui.theme.NavActiveBlue
import com.waste.link.ui.theme.NavBackground
import com.waste.link.ui.theme.NavInactiveGray

@Composable
fun WasteLinkBNB(navController: NavController) {
    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Container: White background, top rounded corners, subtle shadow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    spotColor = Color(0x1F17335C),
                    ambientColor = Color(0x1F17335C)
                )
                .background(
                    color = NavBackground,
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                )
                .padding(
                    start = 18.dp,
                    end = 18.dp,
                    top = 14.dp,
                    bottom = 14.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BnbTabs.forEachIndexed { index, tab ->
                val isSelected = currentRoute == tab.route
                val isCenter = index == 2 // 3rd item is the center
                
                val itemColor = if (isSelected) NavActiveBlue else NavInactiveGray

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, // No ripple to match reference cleanly
                            onClick = {
                                if (!isSelected) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isCenter) {
                            // Spacer for the center item since the diamond icon sits above it
                            Spacer(modifier = Modifier.size(22.dp))
                        } else {
                            Icon(
                                imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                                contentDescription = stringResource(id = tab.labelResId),
                                modifier = Modifier.size(22.dp),
                                tint = itemColor
                            )
                        }
                        
                        Text(
                            text = stringResource(id = tab.labelResId),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.1.sp,
                            color = itemColor,
                            lineHeight = 10.5.sp
                        )
                    }
                }
            }
        }

        // Center Overlapping Diamond
        val centerTab = BnbTabs[2]
        val isCenterSelected = currentRoute == centerTab.route

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // Use absolute offset to break out and overlap the top edge
                .offset(y = (-20).dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!isCenterSelected) {
                            navController.navigate(centerTab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
        ) {
            // The diamond background
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = Color(0x661F8FE0),
                        ambientColor = Color(0x661F8FE0)
                    )
                    .rotate(45f) // Rotate rounded square to make diamond
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(DiamondGradientFrom, DiamondGradientTo)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Rotate icon back so it's upright
                Icon(
                    imageVector = if (isCenterSelected) centerTab.filledIcon else centerTab.outlinedIcon,
                    contentDescription = stringResource(id = centerTab.labelResId),
                    modifier = Modifier
                        .size(23.dp)
                        .rotate(-45f),
                    tint = Color.White
                )
            }

            // Notification Badge inside a white circle
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .shadow(elevation = 5.dp, shape = CircleShape, spotColor = Color(0x2E000000))
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = DiamondGradientTo
                )
            }
        }
    }
}
