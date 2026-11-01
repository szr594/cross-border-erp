# Frontend Route Design

| Route | Page | Initial access rule |
| --- | --- | --- |
| `/login` | Login | Public |
| `/` | Redirect to orders | Authenticated |
| `/orders` | Order workspace | All authenticated roles |
| `/orders/:orderId` | Order detail | All authenticated roles; fields are role-filtered by API |
| `/orders/imports` | CSV order import | Owner only; implemented after list/detail |
| `/procurement` | Procurement workspace | Authenticated |
| `/inventory` | Inventory list | Authenticated |
| `/inventory/:skuId` | Inventory detail | Authenticated |
| `/receiving` | Domestic receiving | Authenticated |
| `/packing` | Packing workspace | Authenticated |
| `/packing/:orderId` | Packing detail | Authenticated |
| `/:pathMatch(.*)*` | Not found | Public |

The JWT route guard is active. Frontend route metadata improves navigation, but backend authorization and response-field filtering remain authoritative. The approved order page boundaries are defined in `order-spec.md`.
