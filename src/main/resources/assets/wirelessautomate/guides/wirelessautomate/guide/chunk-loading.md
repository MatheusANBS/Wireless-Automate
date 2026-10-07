---
navigation:
  title: Chunk Loading Upgrade
  icon: wirelessautomate:chunk_loader_upgrade
  parent: index.md
  position: 10
item_ids:
  - wirelessautomate:chunk_loader_upgrade
---


# Chunk Loading Upgrade

<ItemImage id="wirelessautomate:chunk_loader_upgrade" scale="2" float="left" />

Keeps the router's chunk (and its machine) loaded, so it keeps working while you're away.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Goes in** | The slot in the router screen's header. |
| **Limit** | Each player keeps up to 16 chunks; several routers in one chunk count once. |

## States

| State in the header | Meaning |
| --- | --- |
| **Active: chunks loaded** | The router and the machine keep working away from players. |
| **Inactive: owner's chunk limit** | Whoever placed the upgrade already forces as many chunks as allowed. |
| **Inactive: disabled on the server** | The server has turned this upgrade off. |

## Recipe

<RecipeFor id="wirelessautomate:chunk_loader_upgrade" />
