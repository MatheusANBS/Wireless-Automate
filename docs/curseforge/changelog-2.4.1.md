Wireless Automate 2.4.1 fixes unwanted automatic network assignment when configuring a machine face.

**Explicit face networks**

- Choosing Extract, Insert or Storage on a face without a network no longer assigns the active network selected in the Network Tablet or Linker.
- Opening the router or its panel in the Tablet does not assign a network. Choose the face's network explicitly in the Network row or use the Linker.
- Changing an action keeps any network already assigned to the face. Explicit Linker operations and copied configurations continue to work as before.

**Other changes**

- Updated the English and Portuguese hints, guide and documentation to explain explicit network selection.
- The interface layout is unchanged in this patch.

**Compatibility note:** the network protocol remains 27, unchanged from 2.4.0. No world or configuration migration is required; existing face networks are preserved. Install the update on the server or single-player instance to apply the fix. NeoForge 21.1.248 or newer is still required, and all optional integrations remain optional.
