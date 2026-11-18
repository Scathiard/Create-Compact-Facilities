# Create: Canned Facilities

[简体中文](README.md)

A Create add-on for Minecraft 1.21.1 on NeoForge. Build a bulk supply machine into a single block by investing the parts and resources that the installation calls for.

**Canned Fluid Source.** Craft a blank from a Fluid Tank and a Hose Pulley. A Spout fills the same workpiece one bucket at a time. The first fill binds the fluid and sets the number of fills needed. Move the unfinished piece between Depots and Mechanical Belts to continue working on it. The level rises behind its copper-framed glass as it fills. Once placed, the finished source supplies its bound fluid to a pump through any of its six faces.

Fluid eligibility follows Create’s bottomless Hose Pulley settings. Water and lava qualify by default. **Water takes exactly 3 fills. Lava takes 10,000 fills by default.** Each fill consumes one bucket. For lava and other eligible non-water fluids, modpacks can set `requiredFills` in `canned_facilities-server.toml` to change how many fills a new workpiece needs. The default value of `0` follows the Hose Pulley threshold.
