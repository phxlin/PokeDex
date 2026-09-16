package com.pokedex.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pokedex.app.ui.theme.DexRedDark
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pokedex.app.ui.camera.CameraScreen
import com.pokedex.app.ui.detail.DetailScreen
import com.pokedex.app.ui.list.PokemonListScreen
import com.pokedex.app.ui.result.ResultScreen
import com.pokedex.app.ui.team.TeamEditorScreen
import com.pokedex.app.ui.team.TeamsListScreen
import kotlinx.coroutines.launch

private data class Tab(val label: String, val icon: @Composable () -> Unit)

private val HomeTabs = listOf(
    Tab("Pokédex") { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
    Tab("Teams") { Icon(Icons.Default.Groups, contentDescription = null) },
    Tab("Identify") { Icon(Icons.Default.CameraAlt, contentDescription = null) },
)

@Composable
fun PokeDexNavHost() {
    val navController = rememberNavController()
    // Hoisted out of HomePager so every screen's "Home" means the same thing:
    // back to the Pokédex tab, whether that's a pager-tab switch (already on
    // Routes.HOME) or a pop from a pushed route (Detail/TeamEditor) followed
    // by one — popping alone would leave the pager on whichever tab it was
    // last showing, not necessarily Pokédex.
    val pagerState = rememberPagerState(pageCount = { HomeTabs.size })
    val scope = rememberCoroutineScope()
    fun goHome() {
        navController.popBackStack(Routes.HOME, inclusive = false)
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.HOME) {
            HomePager(
                pagerState = pagerState,
                onPokemonClick = { navController.navigate(Routes.detail(it)) },
                onOpenTeam = { id -> navController.navigate(Routes.teamEditor(id)) },
                onOpenPokemon = { idOrName, banner ->
                    navController.navigate(Routes.detail(idOrName, banner))
                },
                onShowResult = { payload -> navController.navigate(Routes.result(payload)) },
                onHome = ::goHome,
            )
        }
        composable(
            route = Routes.TEAM_EDITOR,
            arguments = listOf(navArgument(Routes.ARG_TEAM_ID) { type = NavType.StringType }),
        ) {
            TeamEditorScreen(
                onBack = { navController.popBackStack() },
                onHome = ::goHome,
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument(Routes.ARG_ID_OR_NAME) { type = NavType.StringType },
                navArgument(Routes.ARG_BANNER) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            DetailScreen(
                idOrName = entry.arguments?.getString(Routes.ARG_ID_OR_NAME).orEmpty(),
                onBack = { navController.popBackStack() },
                onHome = ::goHome,
                onEvolutionClick = { id ->
                    // No launchSingleTop: each hop is its own entry so its
                    // ViewModel re-reads the new id and the back stack works.
                    navController.navigate(Routes.detail(id))
                },
            )
        }
        composable(
            route = Routes.RESULT,
            arguments = listOf(navArgument(Routes.ARG_PAYLOAD) { type = NavType.StringType }),
        ) { entry ->
            ResultScreen(
                payload = entry.arguments?.getString(Routes.ARG_PAYLOAD).orEmpty(),
                onOpenPokemon = { id ->
                    navController.navigate(Routes.detail(id)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onTryAgain = { navController.popBackStack() },
            )
        }
    }
}

/**
 * The three main sections (Pokédex / Teams / Identify), swipeable via [HorizontalPager]
 * as well as tappable from the bottom bar — both drive the same [pagerState], so a swipe
 * and a tap keep the indicator and the visible page in sync.
 */
@Composable
private fun HomePager(
    pagerState: PagerState,
    onPokemonClick: (String) -> Unit,
    onOpenTeam: (Long) -> Unit,
    onOpenPokemon: (String, String?) -> Unit,
    onShowResult: (String) -> Unit,
    onHome: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            NavigationBar(
                containerColor = DexRedDark,
                contentColor = Color.White,
            ) {
                HomeTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = tab.icon,
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DexRedDark,
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                            unselectedTextColor = Color.White.copy(alpha = 0.7f),
                            indicatorColor = Color.White,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { page ->
            when (page) {
                0 -> PokemonListScreen(onPokemonClick = onPokemonClick)
                1 -> TeamsListScreen(
                    onOpenTeam = onOpenTeam,
                    onHome = onHome,
                )
                else -> CameraScreen(onOpenPokemon = onOpenPokemon, onShowResult = onShowResult)
            }
        }
    }
}
