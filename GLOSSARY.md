# Domain Glossary

| Term | Meaning |
| --- | --- |
| Ready for packing | A query-derived order state where every normal item is fully covered by active inventory reservations and no packing task exists. |
| Packing task | The single lifecycle record that coordinates packing one sales order. |
| Packing box | One physical carton belonging to a draft packing task. |
| Box item | A quantity placed in a box and traced to one inventory allocation and sales-order item. |
| Packing completion | The irreversible first-phase command that validates box coverage and consumes all reserved allocations atomically. |
| Inventory allocation | A traceable reservation of one inventory lot for one sales-order item. |
| Packing consumed | An immutable inventory movement created when a reserved allocation is consumed by packing completion. |
