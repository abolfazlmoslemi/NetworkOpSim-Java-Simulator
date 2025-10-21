# Network Operator Simulator (NetworkOpSim)

![Java](https://img.shields.io/badge/Java-17-blue.svg?style=for-the-badge&logo=java) ![Maven](https://img.shields.io/badge/Build-Maven-red.svg?style=for-the-badge&logo=apache-maven) ![License](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)

A comprehensive case study in software architecture, demonstrating the evolution of a real-time multiplayer network simulation game. This project chronicles the journey from a simple monolith to a scalable, multi-module client-server application, with each major phase meticulously preserved in its own Git branch.

---

### 🏛️ **Architectural Evolution: A Three-Phase Journey**

This project's development is structured across three primary branches, each representing a distinct stage in its architectural maturity. This organization provides a clear and tangible timeline of the software's evolution, from a simple prototype to a fully distributed system.

> **To explore the code for any phase, simply switch to the corresponding branch.**

---

*   ### **Phase 1: The Monolithic Foundation**
    > **Branch:** **[`faz1nahaee`](https://github.com/abolfazlmoslemi/NetworkOpSim-Java-Simulator/tree/faz1nahaee)**
    *   **Architecture:** A standard desktop Swing application with a tightly-coupled, monolithic structure. The `GamePanel` class encapsulates the responsibilities of Model, View, and Controller.
    *   **Objective:** Rapid prototyping of core game mechanics, rendering, and input handling to establish a functional baseline.

---

*   ### **Phase 2: Decoupling & Design Patterns**
    > **Branch:** **[`faz2nahaee`](https://github.com/abolfazlmoslemi/NetworkOpSim-Java-Simulator/tree/faz2nahaee)**
    *   **Architecture:** The core simulation logic was refactored into a platform-agnostic `GameEngine`, decoupling it from the Swing framework. The **Strategy Pattern** was implemented to manage diverse network system behaviors (`SystemBehavior`), dramatically improving extensibility.
    *   **Objective:** Enhance testability and maintainability, and prepare the core logic for use in a distributed environment, adhering to MVC and SOLID principles.

---

*   ### **Phase 3: Client-Server & Multiplayer Architecture (Final Version)**
    > **Branch:** **[`faz3nahaee`](https://github.com/abolfazlmoslemi/NetworkOpSim-Java-Simulator/tree/faz3nahaee)**
    *   **Architecture:** The project was modularized into four Maven modules (`core`, `shared`, `client`, `server`). An authoritative server now manages all game state and logic, with communication handled via a DTO-based protocol. A dynamic **Proxy Pattern** was implemented for centralized, AOP-like exception handling on the server.
    *   **Objective:** Implement real-time multiplayer functionality, centralized state management, a scalable backend, and robust error handling.

---

### ✨ **Key Features & Architectural Highlights**

*   **Multi-Module Maven Architecture:** Enforces a strong Separation of Concerns (SoC) between the data model, core logic, client presentation, and server orchestration.
*   **Strategy Design Pattern:** Allows for flexible and extensible network system behaviors, adhering to the Open/Closed Principle.
*   **Proxy Design Pattern (AOP-style):** Provides clean, centralized exception handling on the server, decoupling this cross-cutting concern from the primary business logic.
*   **DTO-Based Network Protocol:** Ensures a stable, versionable, and decoupled API between the client and server.
*   **Real-time Multiplayer Engine:** Features a state machine-driven `GameSession` manager for handling complex game phases, including a strategic "Overtime Build Phase" with penalty mechanics.
*   **Offline Mode with Data Integrity Validation (DIV):** Supports full offline play by running a local `GameEngine` instance and includes a replay system (`ReplayRecorder`) for server-side validation to prevent cheating.
*   **Deterministic Simulation Core:** Utilizes a fixed time-step and a seeded random generator, crucial for replays and fair DIV.

---

### 🛠️ **Tech Stack**

*   **Language:** Java 17
*   **UI Framework:** Java Swing
*   **Build Tool:** Apache Maven
*   **Networking:** Java Sockets (TCP/IP) & Object Serialization
*   **Logging:** SLF4J
*   **Database (Server):** SQLite
*   **Documentation:** LaTeX with TikZ for the accompanying technical paper.

---

### 🚀 **Getting Started (Running the Final Multiplayer Version)**

Follow these steps to build and run the final version of the project from the `faz3nahaee` branch.

1.  **Prerequisites:**
    *   JDK 17 or later
    *   Apache Maven 3.8 or later
    *   Git

2.  **Clone and Checkout the Final Branch:**
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

### 📚 **Exploring Previous Phases**

To explore the code for a specific architectural phase, simply check out the corresponding branch after cloning the repository:

```bash
# Example: Switch to the Phase 2 branch to see the decoupled architecture
git checkout faz2nahaee

# To return to the final version
git checkout faz3nahaee
