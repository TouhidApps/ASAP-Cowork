package bd.asap.cowork.toolintegrations

import bd.asap.cowork.agentsdk.Workspace
import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import java.io.File

/**
 * Scaffolds a new backend project — one tool across four stacks
 * (`spring-boot`, `node-express`, `python-fastapi`, `php`) and three
 * database choices (`mysql`, `postgres`, `sqlite`), each producing a
 * working REST CRUD API around one example `Item` resource plus a real,
 * browsable admin UI for it:
 *
 * - **spring-boot**: generated via the real Spring Initializr HTTP API
 *   (same "let the real tool own the format" reasoning as
 *   [FlutterProjectTool]) with Spring Data REST + its HAL Explorer —
 *   `Item`/`ItemRepository` are the only hand-written files, the CRUD API
 *   and a full browsable admin UI come free from Spring Data REST once a
 *   `@RepositoryRestResource` is declared.
 * - **node-express**: `npm init` + `npm install` (letting npm resolve its
 *   own compatible dependency versions, like [ReactNativeProjectTool]'s
 *   `npm install` step) for Express + Sequelize + AdminJS — a real,
 *   widely-used OSS library that auto-generates a full CRUD admin UI from
 *   Sequelize models, same "real tool over hand-rolled UI" reasoning.
 * - **python-fastapi**: a venv + pip install of FastAPI + SQLAlchemy +
 *   SQLAdmin, which does the same job for FastAPI that AdminJS does for
 *   Express.
 * - **php**: no Composer is assumed available, so this is deliberately
 *   dependency-free — hand-rolled PDO + a minimal HTML admin page, closer
 *   in spirit to [AndroidProjectTool]'s hand-written template than to the
 *   other three stacks' "defer to a real generator" approach.
 *
 * `sqlite` isn't a first-class Spring Data JPA dialect, so the
 * `spring-boot` branch substitutes an embedded, file-based H2 database
 * for it instead — documented in the tool's own result text so this
 * isn't a silent substitution.
 */
object BackendProjectTool {
    const val NAME = "create_backend_project"
    private val NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9]*$")
    private val PACKAGE_NAME_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    private val STACKS = setOf("spring-boot", "node-express", "python-fastapi", "php")
    private val DATABASES = setOf("mysql", "postgres", "sqlite")

    val spec = ToolSpec(
        name = NAME,
        description = "Scaffolds a new backend project as a subdirectory of the workspace: a public landing page at \"/\", a working REST CRUD API around one example \"Item\" resource, plus a real browsable admin UI for it. stack: \"spring-boot\" (Kotlin, via Spring Initializr — admin UI is Spring Data REST's HAL Explorer), \"node-express\" (Express + Sequelize — admin UI is AdminJS), \"python-fastapi\" (FastAPI + SQLAlchemy — admin UI is SQLAdmin), or \"php\" (plain PDO, no framework — hand-rolled admin page, since Composer isn't assumed available). database: \"mysql\", \"postgres\", or \"sqlite\" (spring-boot substitutes embedded H2 for sqlite — noted in the result).",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "name" to mapOf(
                    "type" to "string",
                    "description" to "Project directory name — must start with an uppercase or lowercase letter, letters and digits only, e.g. \"MyBackend\".",
                ),
                "stack" to mapOf("type" to "string", "enum" to STACKS.toList()),
                "database" to mapOf("type" to "string", "enum" to DATABASES.toList()),
                "packageName" to mapOf(
                    "type" to "string",
                    "description" to "Only used by stack=\"spring-boot\": Java/Kotlin package name, e.g. com.example.app. Defaults to com.asap.<name, lowercased>.",
                ),
            ),
            "required" to listOf("name", "stack", "database"),
        ),
    )

    suspend fun execute(workspaceRoot: File, input: Map<String, Any?>, onProgress: suspend (String) -> Unit = {}): ToolResult {
        val name = (input["name"] as? String)?.trim().orEmpty()
        if (!NAME_PATTERN.matches(name)) {
            return ToolResult("\"name\" must start with a letter and contain only letters and digits, e.g. \"MyBackend\".", isError = true)
        }
        val stack = input["stack"] as? String
        if (stack !in STACKS) return ToolResult("\"stack\" must be one of: ${STACKS.joinToString()}.", isError = true)
        val database = input["database"] as? String
        if (database !in DATABASES) return ToolResult("\"database\" must be one of: ${DATABASES.joinToString()}.", isError = true)

        val workspace = Workspace(workspaceRoot.toPath())
        val projectDirPath = workspace.resolve(name)
            ?: return ToolResult("\"$name\" isn't a valid project directory name.", isError = true)
        val projectDir = projectDirPath.toFile()
        if (projectDir.exists()) {
            return ToolResult("A project named \"$name\" already exists in the workspace.", isError = true)
        }

        return when (stack) {
            "spring-boot" -> scaffoldSpringBoot(workspaceRoot, projectDir, name, database!!, input, onProgress)
            "node-express" -> scaffoldNodeExpress(projectDir, name, database!!, onProgress)
            "python-fastapi" -> scaffoldPythonFastApi(projectDir, name, database!!, onProgress)
            "php" -> scaffoldPhp(projectDir, name, database!!)
            else -> ToolResult("Unknown stack: $stack", isError = true)
        }
    }

    // ---------------------------------------------------------------
    // spring-boot
    // ---------------------------------------------------------------

    private suspend fun scaffoldSpringBoot(
        workspaceRoot: File,
        projectDir: File,
        name: String,
        database: String,
        input: Map<String, Any?>,
        onProgress: suspend (String) -> Unit,
    ): ToolResult {
        val packageName = (input["packageName"] as? String)?.trim()?.ifBlank { null } ?: "com.asap.${name.lowercase()}"
        if (!PACKAGE_NAME_PATTERN.matches(packageName)) {
            return ToolResult("\"packageName\" must be a valid reverse-DNS identifier, e.g. com.example.app.", isError = true)
        }

        val dbDependency = when (database) {
            "mysql" -> "mysql"
            "postgres" -> "postgresql"
            else -> "h2"
        }
        val zipFile = File.createTempFile("spring-init-", ".zip")
        val (curlSuccess, curlOutput) = ProcessRunner.run(
            command = listOf(
                "curl", "-sS", "-G", "https://start.spring.io/starter.zip",
                "--data-urlencode", "type=gradle-project-kotlin",
                "--data-urlencode", "language=kotlin",
                "--data-urlencode", "javaVersion=21",
                "--data-urlencode", "groupId=$packageName",
                "--data-urlencode", "artifactId=${name.lowercase()}",
                "--data-urlencode", "name=$name",
                "--data-urlencode", "packageName=$packageName",
                "--data-urlencode", "dependencies=web,data-jpa,data-rest,data-rest-explorer,$dbDependency",
                "-o", zipFile.absolutePath,
            ),
            workDir = workspaceRoot,
            timeoutSeconds = 60,
            maxOutputChars = 2_000,
            progressPrefix = "Generating Spring Boot project",
            onProgress = onProgress,
        )
        if (!curlSuccess) {
            zipFile.delete()
            return ToolResult("Failed to reach Spring Initializr (https://start.spring.io):\n$curlOutput", isError = true)
        }

        val (unzipSuccess, unzipOutput) = ProcessRunner.run(
            command = listOf("unzip", "-q", zipFile.absolutePath, "-d", projectDir.absolutePath),
            workDir = workspaceRoot,
            timeoutSeconds = 30,
            maxOutputChars = 500,
            progressPrefix = "Extracting",
        )
        if (!unzipSuccess) {
            // Spring Initializr reports errors (e.g. an unresolvable dependency) as a small JSON body with a 200/500 status, not a real zip.
            val errorBody = runCatching { zipFile.readText().take(1_000) }.getOrDefault(unzipOutput)
            zipFile.delete()
            return ToolResult("Spring Initializr didn't return a valid project:\n$errorBody", isError = true)
        }
        zipFile.delete()
        File(projectDir, "gradlew").setExecutable(true)

        val workspace = Workspace(projectDir.toPath())
        val packagePath = packageName.replace('.', '/')
        workspace.write(
            "src/main/kotlin/$packagePath/Item.kt",
            """
            |package $packageName
            |
            |import jakarta.persistence.Entity
            |import jakarta.persistence.GeneratedValue
            |import jakarta.persistence.Id
            |
            |@Entity
            |class Item(
            |    var name: String = "",
            |    var description: String = "",
            |) {
            |    @Id
            |    @GeneratedValue
            |    var id: Long? = null
            |}
            |
            """.trimMargin(),
        )
        workspace.write(
            "src/main/kotlin/$packagePath/ItemRepository.kt",
            """
            |package $packageName
            |
            |import org.springframework.data.repository.CrudRepository
            |import org.springframework.data.rest.core.annotation.RepositoryRestResource
            |
            |@RepositoryRestResource(path = "items")
            |interface ItemRepository : CrudRepository<Item, Long>
            |
            """.trimMargin(),
        )

        val dbName = name.lowercase()
        val datasourceProperties = when (database) {
            "mysql" -> """
                |spring.datasource.url=jdbc:mysql://localhost:3306/$dbName?useSSL=false&serverTimezone=UTC
                |spring.datasource.username=root
                |spring.datasource.password=
                |spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
            """.trimMargin()
            "postgres" -> """
                |spring.datasource.url=jdbc:postgresql://localhost:5432/$dbName
                |spring.datasource.username=postgres
                |spring.datasource.password=
                |spring.datasource.driver-class-name=org.postgresql.Driver
            """.trimMargin()
            else -> """
                |spring.datasource.url=jdbc:h2:file:./data/$dbName
                |spring.datasource.driver-class-name=org.h2.Driver
                |spring.h2.console.enabled=true
                |spring.h2.console.path=/h2-console
            """.trimMargin()
        }
        workspace.write(
            "src/main/resources/application.properties",
            """
            |spring.application.name=$name
            |$datasourceProperties
            |spring.jpa.hibernate.ddl-auto=update
            |spring.data.rest.base-path=/api
            |
            """.trimMargin(),
        )
        // Spring Boot's default web starter serves static/ at "/" with no
        // extra code — Spring Data REST only claims /api, so this doesn't
        // collide with it.
        workspace.write("src/main/resources/static/index.html", landingPageHtml(name, "/api/items", "/api/explorer/index.html"))

        val dbNote = if (database == "sqlite") {
            "\n\nsqlite isn't a first-class Spring Data JPA dialect, so this uses an embedded, file-based H2 database instead (zero setup, same JPA/Hibernate code path)."
        } else {
            "\n\nRequires a $database server reachable at the default local port with the placeholder credentials in application.properties — update them to match your actual server."
        }
        return ToolResult(
            "Created Spring Boot project \"$name\" at ${projectDir.absolutePath} (package $packageName). " +
                "run_gradle (task \"bootRun\") or manage_backend_server (stack=\"spring-boot\") to run it — " +
                "landing page at /, REST CRUD API at /api/items, browsable admin UI (HAL Explorer) at /api/explorer/index.html.$dbNote",
        )
    }

    // ---------------------------------------------------------------
    // node-express
    // ---------------------------------------------------------------

    private suspend fun scaffoldNodeExpress(
        projectDir: File,
        name: String,
        database: String,
        onProgress: suspend (String) -> Unit,
    ): ToolResult {
        projectDir.mkdirs()
        val (initSuccess, initOutput) = ProcessRunner.run(
            command = listOf("npm", "init", "-y"),
            workDir = projectDir,
            timeoutSeconds = 60,
            maxOutputChars = 2_000,
            progressPrefix = "npm init",
        )
        if (!initSuccess) return ToolResult("Failed to initialize the npm project:\n$initOutput", isError = true)

        // adminjs/@adminjs/* ship as ESM-only (no "require" export condition) —
        // verified live: a plain CJS require() throws ERR_PACKAGE_PATH_NOT_EXPORTED.
        // "npm pkg set" is the real npm CLI for editing package.json, same
        // "let the real tool own the format" reasoning as everywhere else here.
        val (typeSuccess, typeOutput) = ProcessRunner.run(
            command = listOf("npm", "pkg", "set", "type=module"),
            workDir = projectDir,
            timeoutSeconds = 30,
            maxOutputChars = 500,
            progressPrefix = "npm pkg set",
        )
        if (!typeSuccess) return ToolResult("Failed to configure package.json as an ES module:\n$typeOutput", isError = true)

        val dbDriver = when (database) {
            "mysql" -> "mysql2"
            "postgres" -> "pg pg-hstore"
            else -> "sqlite3"
        }
        val (installSuccess, installOutput) = ProcessRunner.run(
            command = listOf("npm", "install", "express", "sequelize", "adminjs", "@adminjs/express", "@adminjs/sequelize", "express-formidable", "express-session") + dbDriver.split(" "),
            workDir = projectDir,
            timeoutSeconds = 300,
            maxOutputChars = 4_000,
            progressPrefix = "npm install",
            onProgress = onProgress,
        )
        if (!installSuccess) return ToolResult("Created package.json, but \"npm install\" failed:\n$installOutput", isError = true)

        val workspace = Workspace(projectDir.toPath())
        val dialect = when (database) {
            "mysql" -> "mysql"
            "postgres" -> "postgres"
            else -> "sqlite"
        }
        val sequelizeConfig = if (database == "sqlite") {
            """
            |const sequelize = new Sequelize({
            |  dialect: "sqlite",
            |  storage: path.join(__dirname, "data.sqlite"),
            |  logging: false,
            |});
            """.trimMargin()
        } else {
            """
            |const sequelize = new Sequelize(
            |  process.env.DB_NAME || "${name.lowercase()}",
            |  process.env.DB_USER || "${if (database == "mysql") "root" else "postgres"}",
            |  process.env.DB_PASSWORD || "",
            |  {
            |    host: process.env.DB_HOST || "localhost",
            |    port: process.env.DB_PORT || ${if (database == "mysql") 3306 else 5432},
            |    dialect: "$dialect",
            |    logging: false,
            |  },
            |);
            """.trimMargin()
        }

        workspace.write(
            "index.js",
            """
            |import path from "path";
            |import { fileURLToPath } from "url";
            |import express from "express";
            |import formidableMiddleware from "express-formidable";
            |import { Sequelize, DataTypes } from "sequelize";
            |import AdminJS from "adminjs";
            |import { buildAuthenticatedRouter } from "@adminjs/express";
            |import { Database, Resource } from "@adminjs/sequelize";
            |
            |const __dirname = path.dirname(fileURLToPath(import.meta.url));
            |
            |AdminJS.registerAdapter({ Database, Resource });
            |
            |$sequelizeConfig
            |
            |const Item = sequelize.define("Item", {
            |  name: { type: DataTypes.STRING, allowNull: false },
            |  description: { type: DataTypes.STRING },
            |});
            |
            |const app = express();
            |app.use(express.json());
            |
            |const admin = new AdminJS({ resources: [{ resource: Item }], rootPath: "/admin" });
            |const adminRouter = buildAuthenticatedRouter(admin, {
            |  authenticate: async (email, password) => {
            |    const adminEmail = process.env.ADMIN_EMAIL || "admin@example.com";
            |    const adminPassword = process.env.ADMIN_PASSWORD || "admin"; // change this for anything beyond local dev
            |    return email === adminEmail && password === adminPassword ? { email } : null;
            |  },
            |  cookiePassword: process.env.ADMIN_COOKIE_SECRET || "asap-cowork-dev-secret-change-me",
            |});
            |app.use(admin.options.rootPath, formidableMiddleware({ multiples: true }), adminRouter);
            |app.use(express.static(path.join(__dirname, "public")));
            |
            |app.get("/api/items", async (req, res) => res.json(await Item.findAll()));
            |app.get("/api/items/:id", async (req, res) => {
            |  const item = await Item.findByPk(req.params.id);
            |  if (!item) return res.status(404).json({ error: "not found" });
            |  res.json(item);
            |});
            |app.post("/api/items", async (req, res) => res.status(201).json(await Item.create(req.body)));
            |app.put("/api/items/:id", async (req, res) => {
            |  const item = await Item.findByPk(req.params.id);
            |  if (!item) return res.status(404).json({ error: "not found" });
            |  await item.update(req.body);
            |  res.json(item);
            |});
            |app.delete("/api/items/:id", async (req, res) => {
            |  const item = await Item.findByPk(req.params.id);
            |  if (!item) return res.status(404).json({ error: "not found" });
            |  await item.destroy();
            |  res.status(204).end();
            |});
            |
            |const port = process.env.PORT || 8080;
            |sequelize.sync().then(() => {
            |  app.listen(port, () => console.log(`Server listening on ${'$'}{port}`));
            |});
            |
            """.trimMargin(),
        )
        // express.static serves this at "/" — registered after /api and
        // /admin above, but that only matters for path collisions, and
        // none of these overlap.
        workspace.write("public/index.html", landingPageHtml(name, "/api/items", "/admin"))

        val dbNote = if (database == "sqlite") {
            "Uses a local data.sqlite file — no setup needed."
        } else {
            "Requires a $database server reachable at localhost — override DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD env vars if yours differs from the defaults (${if (database == "mysql") "root@localhost:3306" else "postgres@localhost:5432"})."
        }
        return ToolResult(
            "Created Node/Express project \"$name\" at ${projectDir.absolutePath}. " +
                "manage_backend_server (stack=\"node-express\") to run it — landing page at /, REST CRUD API at /api/items, " +
                "admin UI (AdminJS) at /admin (default login admin@example.com / admin — change ADMIN_EMAIL/ADMIN_PASSWORD for anything beyond local dev). $dbNote",
        )
    }

    // ---------------------------------------------------------------
    // python-fastapi
    // ---------------------------------------------------------------

    private suspend fun scaffoldPythonFastApi(
        projectDir: File,
        name: String,
        database: String,
        onProgress: suspend (String) -> Unit,
    ): ToolResult {
        val (venvSuccess, venvOutput) = ProcessRunner.run(
            command = listOf("python3", "-m", "venv", "venv"),
            workDir = projectDir.apply { mkdirs() },
            timeoutSeconds = 60,
            maxOutputChars = 2_000,
            progressPrefix = "Creating virtualenv",
        )
        if (!venvSuccess) return ToolResult("Failed to create a virtualenv (${venvOutput.take(500)}). Is python3 installed?", isError = true)

        val pip = File(projectDir, "venv/bin/pip").absolutePath
        val dbDriver = when (database) {
            "mysql" -> "pymysql"
            "postgres" -> "psycopg2-binary"
            else -> null
        }
        val packages = listOfNotNull("fastapi", "uvicorn[standard]", "sqlalchemy", "sqladmin", "python-multipart", "itsdangerous", dbDriver)
        val (installSuccess, installOutput) = ProcessRunner.run(
            command = listOf(pip, "install") + packages,
            workDir = projectDir,
            timeoutSeconds = 300,
            maxOutputChars = 4_000,
            progressPrefix = "pip install",
            onProgress = onProgress,
        )
        if (!installSuccess) return ToolResult("Created the virtualenv, but \"pip install\" failed:\n$installOutput", isError = true)

        val workspace = Workspace(projectDir.toPath())
        val dbUrl = when (database) {
            "mysql" -> "mysql+pymysql://root:@localhost:3306/${name.lowercase()}"
            "postgres" -> "postgresql+psycopg2://postgres:@localhost:5432/${name.lowercase()}"
            else -> "sqlite:///./data.sqlite"
        }
        workspace.write(
            "main.py",
            """
            |import os
            |
            |from fastapi import FastAPI, HTTPException
            |from fastapi.responses import HTMLResponse
            |from pydantic import BaseModel
            |from sqladmin import Admin, ModelView
            |from sqlalchemy import Column, Integer, String, create_engine
            |from sqlalchemy.orm import Session, declarative_base, sessionmaker
            |
            |DATABASE_URL = os.environ.get("DATABASE_URL", "$dbUrl")
            |connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}
            |engine = create_engine(DATABASE_URL, connect_args=connect_args)
            |SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
            |Base = declarative_base()
            |
            |
            |class Item(Base):
            |    __tablename__ = "items"
            |    id = Column(Integer, primary_key=True, index=True)
            |    name = Column(String, nullable=False)
            |    description = Column(String, default="")
            |
            |
            |Base.metadata.create_all(bind=engine)
            |
            |
            |class ItemIn(BaseModel):
            |    name: str
            |    description: str = ""
            |
            |
            |LANDING_HTML = '''
            |${landingPageHtml(name, "/api/items", "/admin/")}
            |'''
            |
            |app = FastAPI(title="$name")
            |admin = Admin(app, engine)
            |
            |
            |class ItemAdmin(ModelView, model=Item):
            |    column_list = [Item.id, Item.name, Item.description]
            |
            |
            |admin.add_view(ItemAdmin)
            |
            |
            |def get_db():
            |    db = SessionLocal()
            |    try:
            |        yield db
            |    finally:
            |        db.close()
            |
            |
            |@app.get("/api/items")
            |def list_items():
            |    with SessionLocal() as db:
            |        return db.query(Item).all()
            |
            |
            |@app.post("/api/items", status_code=201)
            |def create_item(item: ItemIn):
            |    with SessionLocal() as db:
            |        db_item = Item(name=item.name, description=item.description)
            |        db.add(db_item)
            |        db.commit()
            |        db.refresh(db_item)
            |        return db_item
            |
            |
            |@app.get("/api/items/{item_id}")
            |def get_item(item_id: int):
            |    with SessionLocal() as db:
            |        db_item = db.get(Item, item_id)
            |        if db_item is None:
            |            raise HTTPException(status_code=404, detail="not found")
            |        return db_item
            |
            |
            |@app.delete("/api/items/{item_id}", status_code=204)
            |def delete_item(item_id: int):
            |    with SessionLocal() as db:
            |        db_item = db.get(Item, item_id)
            |        if db_item is None:
            |            raise HTTPException(status_code=404, detail="not found")
            |        db.delete(db_item)
            |        db.commit()
            |
            |
            |# A plain route, not a StaticFiles mount at "/" — verified live: mounting
            |# StaticFiles at "/" makes a bare "/admin" (no trailing slash) 404 instead
            |# of reaching SQLAdmin's own mount, since Starlette only recognizes the
            |# admin mount for "/admin/..." paths, not the bare prefix itself.
            |@app.get("/", response_class=HTMLResponse)
            |async def landing():
            |    return LANDING_HTML
            |
            """.trimMargin(),
        )
        workspace.write(
            "requirements.txt",
            packages.joinToString("\n") + "\n",
        )

        val dbNote = if (database == "sqlite") {
            "Uses a local data.sqlite file — no setup needed."
        } else {
            "Requires a $database server reachable at localhost with default credentials — override the DATABASE_URL env var if yours differs."
        }
        return ToolResult(
            "Created Python/FastAPI project \"$name\" at ${projectDir.absolutePath}. " +
                "manage_backend_server (stack=\"python-fastapi\") to run it — landing page at /, REST CRUD API at /api/items, " +
                "admin UI (SQLAdmin) at /admin/ (trailing slash required), interactive API docs at /docs. $dbNote",
        )
    }

    // ---------------------------------------------------------------
    // php
    // ---------------------------------------------------------------

    private fun scaffoldPhp(projectDir: File, name: String, database: String): ToolResult {
        if (database == "postgres") {
            return ToolResult("\"php\" doesn't support database=\"postgres\" here — use \"mysql\" or \"sqlite\" (pdo_pgsql isn't assumed available).", isError = true)
        }

        val workspace = Workspace(projectDir.toPath())
        val dbName = name.lowercase()
        val pdoDsn = if (database == "mysql") {
            """"mysql:host=" . (getenv("DB_HOST") ?: "localhost") . ";dbname=$dbName;charset=utf8mb4""""
        } else {
            """"sqlite:" . __DIR__ . "/data.sqlite""""
        }
        val pdoCredentials = if (database == "mysql") {
            """getenv("DB_USER") ?: "root", getenv("DB_PASSWORD") ?: """""
        } else {
            "null, null"
        }

        workspace.write(
            "config.php",
            """
            |<?php
            |${'$'}dsn = $pdoDsn;
            |${'$'}pdo = new PDO(${'$'}dsn, $pdoCredentials, [
            |    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            |    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            |]);
            |
            """.trimMargin(),
        )

        workspace.write(
            "schema.sql",
            if (database == "mysql") {
                """
                |CREATE TABLE IF NOT EXISTS items (
                |    id INT AUTO_INCREMENT PRIMARY KEY,
                |    name VARCHAR(255) NOT NULL,
                |    description TEXT
                |);
                |
                """.trimMargin()
            } else {
                """
                |CREATE TABLE IF NOT EXISTS items (
                |    id INTEGER PRIMARY KEY AUTOINCREMENT,
                |    name TEXT NOT NULL,
                |    description TEXT
                |);
                |
                """.trimMargin()
            },
        )

        // A plain .html file, not .php — PHP's built-in dev server serves it
        // directly at "/" with no execution needed, same as any static asset.
        workspace.write("index.html", landingPageHtml(name, "/api/items.php", "/admin/index.php"))

        workspace.write(
            "api/items.php",
            """
            |<?php
            |require __DIR__ . "/../config.php";
            |
            |header("Content-Type: application/json");
            |${'$'}method = ${'$'}_SERVER["REQUEST_METHOD"];
            |${'$'}id = ${'$'}_GET["id"] ?? null;
            |
            |if (${'$'}method === "GET" && ${'$'}id === null) {
            |    ${'$'}stmt = ${'$'}pdo->query("SELECT * FROM items ORDER BY id");
            |    echo json_encode(${'$'}stmt->fetchAll());
            |} elseif (${'$'}method === "GET") {
            |    ${'$'}stmt = ${'$'}pdo->prepare("SELECT * FROM items WHERE id = ?");
            |    ${'$'}stmt->execute([${'$'}id]);
            |    ${'$'}item = ${'$'}stmt->fetch();
            |    if (!${'$'}item) { http_response_code(404); echo json_encode(["error" => "not found"]); exit; }
            |    echo json_encode(${'$'}item);
            |} elseif (${'$'}method === "POST") {
            |    ${'$'}body = json_decode(file_get_contents("php://input"), true) ?? [];
            |    ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO items (name, description) VALUES (?, ?)");
            |    ${'$'}stmt->execute([${'$'}body["name"] ?? "", ${'$'}body["description"] ?? ""]);
            |    http_response_code(201);
            |    echo json_encode(["id" => ${'$'}pdo->lastInsertId()]);
            |} elseif (${'$'}method === "PUT" && ${'$'}id !== null) {
            |    ${'$'}body = json_decode(file_get_contents("php://input"), true) ?? [];
            |    ${'$'}stmt = ${'$'}pdo->prepare("UPDATE items SET name = ?, description = ? WHERE id = ?");
            |    ${'$'}stmt->execute([${'$'}body["name"] ?? "", ${'$'}body["description"] ?? "", ${'$'}id]);
            |    echo json_encode(["updated" => true]);
            |} elseif (${'$'}method === "DELETE" && ${'$'}id !== null) {
            |    ${'$'}stmt = ${'$'}pdo->prepare("DELETE FROM items WHERE id = ?");
            |    ${'$'}stmt->execute([${'$'}id]);
            |    http_response_code(204);
            |} else {
            |    http_response_code(405);
            |    echo json_encode(["error" => "method not allowed"]);
            |}
            |
            """.trimMargin(),
        )

        workspace.write(
            "admin/index.php",
            """
            |<?php
            |require __DIR__ . "/../config.php";
            |
            |if (${'$'}_SERVER["REQUEST_METHOD"] === "POST") {
            |    ${'$'}action = ${'$'}_POST["action"] ?? "";
            |    if (${'$'}action === "create") {
            |        ${'$'}stmt = ${'$'}pdo->prepare("INSERT INTO items (name, description) VALUES (?, ?)");
            |        ${'$'}stmt->execute([${'$'}_POST["name"] ?? "", ${'$'}_POST["description"] ?? ""]);
            |    } elseif (${'$'}action === "delete") {
            |        ${'$'}stmt = ${'$'}pdo->prepare("DELETE FROM items WHERE id = ?");
            |        ${'$'}stmt->execute([${'$'}_POST["id"] ?? 0]);
            |    }
            |    header("Location: index.php");
            |    exit;
            |}
            |
            |${'$'}items = ${'$'}pdo->query("SELECT * FROM items ORDER BY id")->fetchAll();
            |?>
            |<!DOCTYPE html>
            |<html>
            |<head><title>$name admin</title></head>
            |<body>
            |<h1>$name — Items</h1>
            |<form method="post">
            |    <input type="hidden" name="action" value="create">
            |    <input name="name" placeholder="Name" required>
            |    <input name="description" placeholder="Description">
            |    <button type="submit">Add</button>
            |</form>
            |<table border="1" cellpadding="6">
            |<tr><th>ID</th><th>Name</th><th>Description</th><th></th></tr>
            |<?php foreach (${'$'}items as ${'$'}item): ?>
            |<tr>
            |    <td><?= htmlspecialchars(${'$'}item["id"]) ?></td>
            |    <td><?= htmlspecialchars(${'$'}item["name"]) ?></td>
            |    <td><?= htmlspecialchars(${'$'}item["description"]) ?></td>
            |    <td>
            |        <form method="post" style="display:inline">
            |            <input type="hidden" name="action" value="delete">
            |            <input type="hidden" name="id" value="<?= htmlspecialchars(${'$'}item["id"]) ?>">
            |            <button type="submit">Delete</button>
            |        </form>
            |    </td>
            |</tr>
            |<?php endforeach; ?>
            |</table>
            |</body>
            |</html>
            |
            """.trimMargin(),
        )

        if (database == "sqlite") {
            val (schemaSuccess, schemaOutput) = try {
                val process = ProcessBuilder("sqlite3", File(projectDir, "data.sqlite").absolutePath, ".read schema.sql")
                    .directory(projectDir).redirectErrorStream(true).start()
                val finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                (finished && process.exitValue() == 0) to process.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                false to (e.message ?: "unknown error")
            }
            if (!schemaSuccess) {
                return ToolResult(
                    "Created the project, but couldn't initialize the SQLite database automatically ($schemaOutput) — " +
                        "run \"sqlite3 data.sqlite < schema.sql\" yourself in $name/.",
                )
            }
        }

        val dbNote = if (database == "sqlite") {
            "The SQLite database (data.sqlite) has already been initialized with schema.sql."
        } else {
            "Requires a MySQL server reachable at localhost (override DB_HOST/DB_USER/DB_PASSWORD env vars if needed) — " +
                "run \"mysql -u root -p $dbName < schema.sql\" (after creating the \"$dbName\" database) before this will work."
        }
        return ToolResult(
            "Created PHP project \"$name\" at ${projectDir.absolutePath}. " +
                "manage_backend_server (stack=\"php\") to run it via PHP's built-in dev server — landing page at /, REST CRUD API at " +
                "/api/items.php, admin UI at /admin/index.php. $dbNote",
        )
    }

    /** A minimal public landing page every stack serves at "/", separate from the API and admin UI — so a visitor hitting the bare host sees something instead of a 404. */
    private fun landingPageHtml(title: String, apiPath: String, adminPath: String): String = """
        |<!DOCTYPE html>
        |<html>
        |<head>
        |    <meta charset="utf-8">
        |    <title>$title</title>
        |    <style>
        |        body { font-family: system-ui, sans-serif; max-width: 640px; margin: 80px auto; padding: 0 20px; color: #1a1a1a; }
        |        h1 { margin-bottom: 4px; }
        |        p { color: #555; }
        |        a { color: #2563eb; text-decoration: none; }
        |        a:hover { text-decoration: underline; }
        |        .links { margin-top: 24px; display: flex; gap: 16px; }
        |        .links a { border: 1px solid #ddd; border-radius: 8px; padding: 10px 16px; }
        |    </style>
        |</head>
        |<body>
        |    <h1>$title</h1>
        |    <p>Scaffolded by ASAP-Cowork's backend agent — a REST CRUD API plus an admin UI, ready to extend.</p>
        |    <div class="links">
        |        <a href="$apiPath">API</a>
        |        <a href="$adminPath">Admin</a>
        |    </div>
        |</body>
        |</html>
        |
    """.trimMargin()
}
