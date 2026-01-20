This project is a custom implementation of the classic Pacman game in Java. It features a Client-Server architecture, where the server manages the game logic and rendering, while the client runs an autonomous AI algorithm to control Pacman.

📌 Features
Retro UI Design: Classic "Wireframe" style with blue hollow walls on a black background.

Scoring System:

Pink Dots: Standard points (1 point).

Green Power Pellets: Super mode (10 points) – allows Pacman to eat ghosts.

Ghosts: 20 points when eaten in super mode.

Smart AI Algorithm: An autonomous algorithm (Ex3Algo) that navigates Pacman, avoids dangerous ghosts, and hunts them when they are edible.

Map Loading: Supports loading maps from binary files (.bit) or falls back to a built-in classic map layout.

📂 Project Structure
The project is organized into two main packages to separate concerns:

1. Server Package ( The Game Engine)
Responsible for the game rules, board management, and visualization.

Game.java: The main engine. It handles the game loop, drawing (StdDraw), collision detection, and score tracking.

Ghost.java: Represents a ghost entity with its own movement logic (random or chase).

PacmanGame.java: The interface defining the game's API.

Map.java / Map2D.java: (Optional) Helper classes for map representation.

2. Client Package (The Player)
Responsible for running the game and controlling Pacman.

Ex3Main.java: The entry point. It loads the assets, initializes the game, and runs the main loop.

Ex3Algo.java: The brain. Implements PacManAlgo. It analyzes the board state and decides the next best move for Pacman.

GameInfo.java: Configuration file for game settings (ID, level, etc.).

🚀 How to Run
Prerequisites
Java Development Kit (JDK) 8 or higher.

An IDE (IntelliJ IDEA, Eclipse) or Command Line.

Steps
Clone/Download the repository.

Ensure the resources are in place:

Images (p1.png, g0.png...) and test.bit should be in the src folder or root (the code handles extraction if missing).

Compile and Run:

Locate src/Client/Ex3Main.java.

Run the main method.

Start the Game:

A black window will appear displaying: PAUSED - PRESS SPACE TO START.

Click on the window to ensure it has focus.

Press SPACEBAR to start!

🎮 Controls
SPACE: Start / Pause the game.

Esc: Exit.

(Note: The movement is controlled automatically by the AI, but you can pause/resume at any time).

🧠 The Algorithm (Ex3Algo)
The AI uses a smart, heuristic-based approach to maximize score while staying alive:

Safety First (Heat Map):

The algorithm computes a "Heat Map" using BFS (Breadth-First Search) from all dangerous ghosts.

Each cell on the board gets a safety score based on its distance from the nearest ghost.

If Pacman is too close to a ghost (distance < 3), it triggers an Emergency Evasion mode to move to the safest neighbor.

Target Selection:

If ghosts are edible (Power Mode), the algorithm prioritizes hunting them for high points.

Otherwise, it calculates a score for every pellet on the board based on:

Distance (closer is better).

Safety (path must not pass through danger zones).

Type (Power Pellets get higher priority).

Pathfinding:

Uses BFS to find the shortest valid path to the chosen target, treating dangerous ghosts as walls to prevent collisions.

🛠 Troubleshooting
"Space bar doesn't work":

Make sure you clicked on the game window to give it focus.

The game starts in PAUSED mode; pressing Space toggles it to RUNNING.

"Missing ghost images":

The Ex3Main class includes a prepareAssets() method that attempts to extract resources. Ensure your project structure includes the resources folder or that the images are next to the src folder.
