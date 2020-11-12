package no.nav.k9

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.k9.config.Environment
import no.nav.k9.vaktmester.db.ArkivRepository
import no.nav.k9.vaktmester.db.DataSourceBuilder
import no.nav.k9.vaktmester.db.InFlightRepository
import no.nav.k9.vaktmester.river.ArkivRiver
import no.nav.k9.vaktmester.river.InFlightRiver
import javax.sql.DataSource

fun main() {
    val applicationContext = ApplicationContext.Builder().build()
    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(applicationContext.env))
        .withKtorModule { k9Vaktmester(applicationContext) }
        .build()
        .apply { registerApplicationContext(applicationContext) }
        .start()
}

internal fun RapidsConnection.registerApplicationContext(applicationContext: ApplicationContext) {
    ArkivRiver(
        rapidsConnection = this,
        arkivRepository = applicationContext.arkivRepository
    )
    InFlightRiver(
        rapidsConnection = this,
        inFlightRepository = applicationContext.inFlightRepository,
        arkivRepository = applicationContext.arkivRepository
    )
    register(object : RapidsConnection.StatusListener {
        override fun onStartup(rapidsConnection: RapidsConnection) {
            applicationContext.start()
        }
        override fun onShutdown(rapidsConnection: RapidsConnection) {
            applicationContext.stop()
        }
    })
}

internal fun Application.k9Vaktmester(applicationContext: ApplicationContext) {
    install(ContentNegotiation) {
        jackson()
    }

    routing {
        HealthRoute(healthService = applicationContext.healthService)
    }
}

internal class ApplicationContext(
    val env: Environment,
    val dataSource: DataSource,
    val arkivRepository: ArkivRepository,
    val inFlightRepository: InFlightRepository,
    val healthService: HealthService
) {

    internal fun start() {
        DataSourceBuilder(env).migrateAsAdmin()
    }
    internal fun stop() {}

    internal class Builder(
        var env: Environment? = null,
        var dataSource: DataSource? = null,
        var arkivRepository: ArkivRepository? = null,
        var inFlightRepository: InFlightRepository? = null
    ) {
        internal fun build(): ApplicationContext {
            val benyttetEnv = env ?: System.getenv()

            val benyttetDataSource = dataSource ?: DataSourceBuilder(benyttetEnv).getDataSource()
            val benyttetArkivRepository = arkivRepository ?: ArkivRepository(benyttetDataSource)
            val benyttetInFlightRepository = inFlightRepository ?: InFlightRepository(benyttetDataSource)

            return ApplicationContext(
                env = benyttetEnv,
                dataSource = benyttetDataSource,
                arkivRepository = benyttetArkivRepository,
                inFlightRepository = benyttetInFlightRepository,
                healthService = HealthService(
                    healthChecks = setOf(
                        benyttetArkivRepository,
                        benyttetInFlightRepository
                    )
                )
            )
        }
    }
}
