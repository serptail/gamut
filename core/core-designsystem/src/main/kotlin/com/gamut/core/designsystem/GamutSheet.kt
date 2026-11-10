package com.gamut.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamutSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    dragHandle: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalCoverPalette.current
    val centerContent = adaptiveGutter(AdaptiveLayout.SheetContentMaxWidth) > 0.dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = shape,
        containerColor = palette.base,
        contentColor = palette.onBase,
        sheetMaxWidth = Dp.Unspecified,
        dragHandle = {
            CoverTheme(palette) {
                if (dragHandle != null) dragHandle() else BottomSheetDefaults.DragHandle(color = palette.onBase.copy(alpha = 0.3f))
            }
        },
    ) {
        CoverTheme(palette) {
            CompositionLocalProvider(LocalFocusLive provides true) {
                if (centerContent) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(Modifier.widthIn(max = AdaptiveLayout.SheetContentMaxWidth).fillMaxWidth()) { content() }
                    }
                } else {
                    content()
                }
            }
        }
    }
}
