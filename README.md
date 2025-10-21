# Network Operator Simulator (NetworkOpSim)

![Java](https://img.shields.io/badge/Java-17-blue.svg?style=for-the-badge&logo=java) ![Maven](https://img.shields.io/badge/Build-Maven-red.svg?style=for-the-badge&logo=apache-maven) ![License](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)

A comprehensive case study in software architecture, demonstrating the evolution of a real-time multiplayer network simulation game from a simple monolith to a scalable, multi-module client-server application using Java and industry-standard design patterns.

---

### ► Gameplay Demo (Multiplayer Mode)

*A brief demonstration of the final multiplayer gameplay, showcasing real-time packet movement, player-owned networks, and strategic packet deployment.*

*(To add this, create a `demo.gif` of your gameplay, upload it to the root of the `faz3nahaee` branch, and this link will work automatically.)*

![Gameplay Demo](./demo.gif)

---

### 🏛️ Architectural Evolution: A Three-Phase Journey

This project was developed in three distinct phases, with the final code for each phase meticulously preserved in its own branch. This structure provides a clear and tangible timeline of the software's architectural maturation, from a simple prototype to a distributed system.

*   #### **Phase 1: The Monolithic Foundation**
    > **Branch: [`faz1nahaee`](https://github.com/abolfazlmoslemi/NetworkOpSim-Java-Simulator/tree/faz1nahaee)**
    *   **Architecture:** A standard desktop Swing application with a tightly-coupled, monolithic structure. The `GamePanel` class encapsulates the responsibilities of Model, View, and Controller.
    *   **Objective:** Rapid prototyping of core game mechanics, rendering, and input handling to establish a functional baseline.

*   #### **Phase 2: Decoupling & Design Patterns**
    > **Branch: [`faz2nahaee`](https://github.com/abolfazlmoslemi/NetworkOpSim-Java-Simulator/tree/faz2nahaee)**
    *   **Architecture:** The simulation logic was refactored into a platform-agnostic `GameEngine`, decoupling it from the Swing framework. The **Strategy Pattern** was implemented to manage diverse network system behaviors (`SystemBehavior`), dramatically improving extensibility.
    *   **Objective:** Enhance testability and maintainability, and prepare the core logic for use in a distributed environment.

*   #### **Phase 3: Client-Server & Multiplayer Architecture (Final Version)**
    > **Branch: [`faz3nahaee`](https://github.com/abolfazlmoslemi/NetworkOpSim-Java-Simulator/tree/faz3nahaee)**
    *   **Architecture:** The project was modularized into four Maven modules (`core`, `shared`, `client`, `server`). An authoritative server now manages all game state and logic, with communication handled via a DTO-based protocol. A dynamic **Proxy Pattern** was implemented for centralized, AOP-like exception handling on the server.
    *   **Objective:** Implement real-time multiplayer functionality, centralized state management, a scalable backend, and robust error handling.

---

### ✨ Key Features & Architectural Highlights

*   **Multi-Module Maven Architecture:** Enforces a strong Separation of Concerns (SoC) between the data model, core logic, client presentation, and server orchestration.
*   **Strategy Design Pattern:** Allows for flexible and extensible network system behaviors, adhering to the Open/Closed Principle.
*   **Proxy Design Pattern (AOP-style):** Provides clean, centralized exception handling on the server, decoupling this cross-cutting concern from the primary business logic.
*   **DTO-Based Network Protocol:** Ensures a stable, versionable, and decoupled API between the client and server.
*   **Real-time Multiplayer Engine:** Features a state machine-driven `GameSession` manager for handling complex game phases, including a strategic "Overtime Build Phase" with penalty mechanics.
*   **Offline Mode with Data Integrity Validation (DIV):** Supports full offline play by running a local `GameEngine` instance and includes a replay system (`ReplayRecorder`) for server-side validation to prevent cheating.
*   **Deterministic Simulation Core:** Utilizes a fixed time-step and a seeded random generator, crucial for replays and fair DIV.

---

### 🎨 Architecture Diagrams

*These diagrams were created using LaTeX and TikZ for the project's technical paper and illustrate the final architecture.*

**1. Modular Architecture Overview**
*(Upload your module diagram to a `docs/diagrams` folder in the `faz3nahaee` branch and update the path below.)*
`![Module Diagram](./docs/diagrams/module-diagram.png)`

**2. Strategy Pattern for `SystemBehavior`**
*(Upload your Strategy pattern diagram.)*
`![Strategy Pattern Diagram](./docs/diagrams/strategy-diagram.png)`

**3. Proxy-based Exception Handling Flow**
*(Upload your Proxy pattern diagram.)*
`![Proxy Pattern Diagram](./docs/diagrams/proxy-diagram.png)`

---

### 🛠️ Tech Stack

*   **Language:** Java 17
*   **UI Framework:** Java Swing
*   **Build Tool:** Apache Maven
*   **Networking:** Java Sockets (TCP/IP) & Object Serialization
*   **Logging:** SLF4J
*   **Database (Server):** SQLite
*   **Documentation:** LaTeX with TikZ

---

### 🚀 Getting Started (Running the Final Multiplayer Version)

Follow these steps to build and run the final version of the project.

1.  **Prerequisites:**
    *   JDK 17 or later
    *   Apache Maven 3.8 or later
    *   Git

2.  **Clone and Checkout Final Branch:**
    ```bash
    git clone https://github.com/abolfazlmoslemi/NetworkOpSim-Java-Simulator.git
    cd NetworkOpSim-Java-Simulator
    git checkout faz3nahaee
    ```

3.  **Build the Project:**
    This command will compile all modules and create the executable JAR files.
    ```bash
    mvn clean install
    ```

4.  **Run the Server:**
    ```bash
    java -jar NetworkOpSim-Server/target/NetworkOpSim-Server-*.jar
    ```
    The server is now running and waiting for clients on port `26263`.

5.  **Run the Client:**
    Open a new terminal for each client instance you want to run.
    ```bash
    java -jar NetworkOpSim-Client/target/NetworkOpSim-Client-*.jar
    ```

---

### 📚 Exploring Previous Phases

To explore the code for a specific architectural phase, simply check out the corresponding branch after cloning the repository:

```bash
# Example: Switch to the Phase 2 branch to see the decoupled architecture
git checkout faz2nahaee

# To return to the final version
git checkout faz3nahaee
