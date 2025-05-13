# Container Item Finder

A Minecraft mod that helps players locate items within containers.

## Command Guide

The mod provides commands to search for and index items in nearby containers:

### Search Command

```
/cif search <range> <item>
```

- `range`: The search radius in blocks (integer)
- `item`: The item to search for (e.g., `diamond`, `iron_ingot`)

#### Advanced Search

```
/cif search <range> <item> <count>
```

- `range`: The search radius in blocks (integer)
- `item`: The item to search for (e.g., `diamond`, `iron_ingot`)
- `count`: The minimum number of items to find (integer)

### Index Command

```
/cif index <range>
```

- `range`: The search radius in blocks (integer)

This command will scan all containers within the specified range and show a summary of all items found, sorted by total count.

### Cancelling task

```
/cif cancel
```

### Examples
- Search for diamonds within 10 blocks: `/cif search 10 diamond`
- Search for 64 iron ingots within 20 blocks: `/cif search 20 iron_ingot 64`
- Index all items in containers within 15 blocks: `/cif index 15`

### Results
The search command will show:
- The total number of items found
- The number of containers containing the items
- The coordinates of each container
- If a specific count was requested, it will show how many more items are needed

The index command will show:
- Total number of items found
- Total number of containers searched
- A list of all items found, sorted by total count
- For each item, it shows the total count and how many containers contain it

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
