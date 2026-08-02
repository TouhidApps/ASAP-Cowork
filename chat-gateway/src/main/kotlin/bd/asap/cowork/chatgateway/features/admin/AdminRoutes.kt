package bd.asap.cowork.chatgateway.features.admin

import bd.asap.cowork.chatgateway.common.ApiResponse
import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.chatgateway.plugins.AdminAuth
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

private fun requirePathParameter(id: String?, label: String): String = id?.takeIf { it.isNotBlank() }
    ?: throw AppException.BadRequest("Missing $label")

fun Route.adminRoutes() {
    val adminService by inject<AdminService>()
    val workspaceService by inject<WorkspaceService>()
    val toolchainService by inject<ToolchainService>()
    val firebaseService by inject<FirebaseService>()
    val firebaseCliService by inject<FirebaseCliService>()
    val ollamaAdminService by inject<OllamaAdminService>()
    val allowedHostsService by inject<AllowedHostsService>()

    route("/api/v1/admin") {
        install(AdminAuth)

        route("/workspace") {
            get {
                call.respond(ApiResponse.ok(workspaceService.status()))
            }

            get("/browse") {
                call.respond(ApiResponse.ok(workspaceService.browse(call.request.queryParameters["path"])))
            }

            post("/confirm") {
                val request = call.receive<ConfirmWorkspaceRequest>()
                call.respond(ApiResponse.ok(workspaceService.confirm(request.path)))
            }

            get("/storage") {
                call.respond(ApiResponse.ok(workspaceService.storageStatus()))
            }

            post("/storage/cleanup") {
                val request = call.receive<CleanupStorageRequest>()
                call.respond(ApiResponse.ok(workspaceService.cleanup(request.target)))
            }

            get("/backup/items") {
                call.respond(ApiResponse.ok(workspaceService.backupItems()))
            }

            post("/backup") {
                val request = call.receive<BackupRequest>()
                call.respond(ApiResponse.ok(workspaceService.backup(request.destination, request.items)))
            }
        }

        route("/toolchain") {
            get {
                call.respond(ApiResponse.ok(toolchainService.status()))
            }

            put {
                val request = call.receive<SetToolchainPathsRequest>()
                call.respond(
                    ApiResponse.ok(
                        toolchainService.update(
                            request.flutterSdkPath,
                            request.androidSdkPath,
                            request.javaHomePath,
                            request.xcodePath,
                            request.xcodeGenPath,
                        ),
                    ),
                )
            }

            post("/install/{component}") {
                val component = requirePathParameter(call.parameters["component"], "toolchain component")
                call.respond(ApiResponse.ok(toolchainService.install(component)))
            }
        }

        route("/allowed-hosts") {
            get {
                call.respond(ApiResponse.ok(allowedHostsService.status()))
            }

            post {
                val request = call.receive<AddAllowedHostRequest>()
                call.respond(ApiResponse.ok(allowedHostsService.add(request.host)))
            }

            delete("/{host}") {
                val host = requirePathParameter(call.parameters["host"], "host")
                call.respond(ApiResponse.ok(allowedHostsService.remove(host)))
            }
        }

        route("/firebase") {
            get {
                call.respond(ApiResponse.ok(firebaseService.status()))
            }

            put {
                val request = call.receive<SetFirebaseCredentialsRequest>()
                call.respond(
                    ApiResponse.ok(
                        firebaseService.setCredentials(request.appId, request.ciToken, request.testerGroups, request.releaseNotes),
                    ),
                )
            }

            delete {
                call.respond(ApiResponse.ok(firebaseService.clearCredentials()))
            }

            post("/generate-ci-token") {
                call.respond(ApiResponse.ok(GenerateCiTokenResult(firebaseCliService.generateCiToken())))
            }

            post("/apps") {
                val request = call.receive<ListFirebaseAppsRequest>()
                call.respond(
                    ApiResponse.ok(ListFirebaseAppsResult(firebaseCliService.listAndroidApps(request.projectId, request.ciToken))),
                )
            }
        }

        get("/status") {
            call.respond(ApiResponse.ok(adminService.status()))
        }

        get("/conversations") {
            call.respond(ApiResponse.ok(adminService.conversations()))
        }

        get("/conversations/{id}/messages") {
            val id = requirePathParameter(call.parameters["id"], "conversation id")
            call.respond(ApiResponse.ok(adminService.conversationMessages(id)))
        }

        delete("/conversations/{id}") {
            val id = requirePathParameter(call.parameters["id"], "conversation id")
            adminService.deleteConversation(id)
            call.respond(ApiResponse.ok(Unit))
        }

        get("/providers") {
            call.respond(ApiResponse.ok(adminService.providers()))
        }

        put("/providers/current") {
            val request = call.receive<SetProviderRequest>()
            call.respond(ApiResponse.ok(adminService.setProvider(request.provider)))
        }

        put("/providers/{id}/credentials") {
            val id = requirePathParameter(call.parameters["id"], "provider id")
            val request = call.receive<SetProviderCredentialRequest>()
            call.respond(ApiResponse.ok(adminService.setProviderCredential(id, request.apiKey)))
        }

        delete("/providers/{id}/credentials") {
            val id = requirePathParameter(call.parameters["id"], "provider id")
            call.respond(ApiResponse.ok(adminService.clearProviderCredential(id)))
        }

        route("/providers/ollama") {
            get("/status") {
                call.respond(ApiResponse.ok(ollamaAdminService.status()))
            }

            post("/install") {
                ollamaAdminService.installOllama()
                call.respond(ApiResponse.ok(ollamaAdminService.status()))
            }

            put("/model") {
                val request = call.receive<SetOllamaModelRequest>()
                ollamaAdminService.setModel(request.model)
                call.respond(ApiResponse.ok(ollamaAdminService.status()))
            }

            post("/pull") {
                val request = call.receive<PullOllamaModelRequest>()
                ollamaAdminService.pullModel(request.model)
                call.respond(ApiResponse.ok(ollamaAdminService.status()))
            }

            delete("/models/{name}") {
                val name = requirePathParameter(call.parameters["name"], "model name")
                ollamaAdminService.deleteModel(name)
                call.respond(ApiResponse.ok(ollamaAdminService.status()))
            }

            post("/models/delete") {
                val request = call.receive<DeleteOllamaModelsRequest>()
                call.respond(ApiResponse.ok(ollamaAdminService.deleteModels(request.models)))
            }
        }
    }
}
