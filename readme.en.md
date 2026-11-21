# Create: Compact Facilities

[简体中文](README.md)

A Create add-on for Minecraft 1.21.1 on NeoForge. Build a bulk supply machine into a single block by investing the parts and resources that the installation calls for.

Craft an **Empty Compact Fluid Source** from a Fluid Tank and a Hose Pulley. A Spout fills the same workpiece one bucket at a time. The first fill binds the fluid and sets the number of fills needed. Move the unfinished piece between Depots and Mechanical Belts to continue working on it. The level rises behind its copper-framed glass as it fills. Once placed, the finished source supplies its bound fluid to a pump through any of its six faces. Engineer’s Goggles show which fluid it supplies and whether the modpack has disabled it.

Any fluid that can form a source block works, including water, lava, honey and chocolate. Create's potion fluid has no source block and cannot be used. **Water takes exactly 3 buckets. Other fluids take 10,000 by default.** Set `requiredFills` in `compact_facilities-server.toml` to change the cost of a new non-water workpiece. The default value of `0` uses the Hose Pulley threshold. Setting Create's `hosePulleyBlockThreshold` to `-1` pauses filling and output while preserving existing bindings.

The **Create: Compact Facilities** creative tab offers an empty workpiece and an unbound finished source. Place the finished block and right-click it with a filled container to set or change its fluid. Right-click with an empty bucket to clear it. Creative players keep their containers. In survival, the placed source keeps its binding.

## License

This project is licensed under the [MIT License](LICENSE).
