# Container Item Finder

A Minecraft mod that helps players locate items within containers.

## Command Guide

The mod provides a command to search for items in nearby containers:

### Basic Usage

```
/cif <range> <item>
```

- `range`: The search radius in blocks (integer)
- `item`: The item to search for (e.g., `diamond`, `iron_ingot`)

### Advanced Usage

```
/cif <range> <item> <count>
```

- `range`: The search radius in blocks (integer)
- `item`: The item to search for (e.g., `diamond`, `iron_ingot`)
- `count`: The minimum number of items to find (integer)

### Cancelling task

```
/cif cancel
```

### Examples
- Search for diamonds within 10 blocks: `/cif 10 diamond`
- Search for 64 iron ingots within 20 blocks: `/cif 20 iron_ingot 64`

### Results
The command will show:
- The total number of items found
- The number of containers containing the items
- The coordinates of each container
- If a specific count was requested, it will show how many more items are needed

## Development

### Prerequisites

- Java 21 or higher
- Gradle

### Building from Source

1. Clone the repository
2. Open a terminal in the project directory
3. Run `./gradlew build`
4. The built mod will be in `build/libs/`

## License

This project is licensed under MIT license.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Contact

For any questions or issues, please open an issue on the GitHub repository.
