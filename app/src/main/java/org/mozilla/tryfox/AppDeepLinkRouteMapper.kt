package org.mozilla.tryfox

object AppDeepLinkRouteMapper {
    fun routeFor(rawValue: String?): String? {
        return when (val destination = AppDeepLinkParser.parse(rawValue)) {
            is AppDeepLinkDestination.TreeherderSearch -> {
                AppRoutes.createTreeherderSearchRoute(
                    project = destination.project,
                    query = destination.revision,
                )
            }

            is AppDeepLinkDestination.Profile -> {
                AppRoutes.createTreeherderSearchRoute(project = destination.project, query = destination.email)
            }

            null -> null
        }
    }
}
