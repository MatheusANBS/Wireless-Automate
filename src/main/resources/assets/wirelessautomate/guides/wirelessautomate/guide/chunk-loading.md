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

Keeps the router's chunk (and its machine) loaded, so it keeps working while you're away. It goes
in the slot in the router screen's header.

<br clear="all" />

<RecipeFor id="wirelessautomate:chunk_loader_upgrade" />

- The header shows its state: **Active**, **Inactive: owner's chunk limit** or **Inactive: disabled
  on the server**.
- Each player forces up to **16 chunks** by default; several routers in one chunk count once.
- The server can disable the upgrade or change the limit (`chunkLoading` in the config).
