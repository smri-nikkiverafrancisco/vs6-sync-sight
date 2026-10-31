# Sync Sight (VS6) - Module Map

Sync Sight compares what the Enterprise server and each Local store server say about promotions.
Read this first to find which code belongs to which module.

## Modules

| Code | Module | What it checks (today = current date) | Package | Status |
|---|---|---|---|---|
| 1.1.1 | Promotions > Ongoing > All Ongoing | activedatefrom <= today <= activedateto | `promotions.ongoing.all` | Planned |
| 1.1.2 | Promotions > Ongoing > Starting | today = activedatefrom | `promotions.ongoing.starting` | Built |
| 1.2 | Promotions > Upcoming | today < activedatefrom | `promotions.upcoming` | Planned |
| 1.3 | Promotions > Ended | today > activedateto | `promotions.ended` | Planned |
| 2.1.1 | Promotion Elements > Ongoing > All Ongoing | same date rule as 1.1.1, elements count | `promotionelements.ongoing.all` | Planned |
| 2.1.2 | Promotion Elements > Ongoing > Starting | same date rule as 1.1.2, elements count | `promotionelements.ongoing.starting` | Planned |
| 2.2 | Promotion Elements > Upcoming | same date rule as 1.2, elements count | `promotionelements.upcoming` | Planned |

Promotions (1) and Promotion Elements (2) are separate modules. They use the same
datasources (Enterprise and Local) but each has its own API call and SQL, so they never import from each other.

All packages are under `com.vs6.syncsight`.

## Package layout

```
shared/                         used by BOTH modules
  config/      WebClientConfig
  auth/        AuthTokenService
  store/       Store, StoreService
  source/      Source (Enterprise/Local), FetchResult
  exception/   ApiResponseException, ConnectionFailedException

promotions/                     MODULE 1
  shared/                       used by 2+ Promotions submodules
    api/       findbyfilter request/response, StorePromotionService
    snapshot/  PromotionSnapshotStore, PromotionSnapshotLoader, RefreshProgress
    status/    PromoStatus
    anomaly/   AnomalyDetector and anomaly models
  ongoing/
    starting/                   1.1.2 (model/, service/, web/)

resources/templates/sync-sight/promotions/ongoing/starting.html
```

## Where does a new file go?

1. Used by one submodule only: inside that submodule's package.
2. Used by 2+ submodules of the same module: that module's `shared` package.
3. Used by both Promotions and Promotion Elements: the top-level `shared` package.

Start at the lowest level and move a file up only when a second user appears.

## How a refresh works (Starting Promotions)

1. The Refresh button calls `PromotionSyncService.startRefresh()` (runs in the background).
2. `PromotionSnapshotLoader` reads all stores and fetches each store's promotions from
   Enterprise and Local (several stores at the same time) into `PromotionSnapshotStore`.
3. `PromotionSyncService` reads today's starting promotions from the database.
4. `StartingPromotionChecker` looks up each promo's status in the snapshot.
5. The report is swapped in and the page (`StartingPromotionsController`) shows it.

## Adding a new submodule

1. Create its package under the right module (for example `promotions.upcoming`).
2. Write its own SQL and checker. Reuse `PromotionSnapshotLoader.loadAll(progress)` for the fetching.
3. Add its controller and template under `templates/sync-sight/promotions/...` with a matching URL path.
4. Add a row to the Modules table above.

## Known to-do

- `promotions.shared.anomaly` still uses the Starting models (`StartingPromotion`,
  `StartingPromotionStatus`, `StoreStatusRow`). When All Ongoing is built, move those models and the
  checker up into `promotions.shared` so shared code no longer depends on a submodule.