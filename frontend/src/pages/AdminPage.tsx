import { Layout } from '../components/Layout'
import { IngestionRunPanel } from '../components/IngestionRunPanel'
import { endpoints } from '../api/endpoints'

export function AdminPage() {
  return (
    <Layout>
      <h1 className="mb-1 text-xl font-semibold text-ink">Administración</h1>
      <p className="mb-6 text-sm text-ink-faint">
        Dispara manualmente un ciclo de ingesta de cuotas fuera del horario programado.
      </p>

      <IngestionRunPanel
        title="OddsPapi"
        description="Fuente principal de cuotas."
        buttonLabel="Disparar ingesta ahora"
        fetchLastRun={endpoints.lastIngestionRun}
        trigger={endpoints.triggerIngestion}
      />

      <IngestionRunPanel
        title="The Odds API"
        description="Fuente complementaria de cuotas."
        buttonLabel="Disparar ingesta The Odds API"
        fetchLastRun={endpoints.lastTheOddsApiIngestionRun}
        trigger={endpoints.triggerTheOddsApiIngestion}
      />
    </Layout>
  )
}
