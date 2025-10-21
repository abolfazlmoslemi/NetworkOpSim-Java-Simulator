# Network Operator Simulator (NetworkOpSim)

![Java](https://img.shields.io/badge/Java-17-blue.svg) ![Maven](https://img.shields.io/badge/Maven-3.8-red.svg) ![License](https://img.shields.io/badge/License-MIT-green.svg)

A real-time multiplayer network simulation game developed as an advanced programming course project. This repository showcases the architectural evolution of a complex software system, from a simple monolith to a scalable, multi-module client-server model in Java.

---

### **Gameplay Demo (Multiplayer Mode)**

*(در این قسمت یک GIF جذاب از گیم‌پلی فاز نهایی (چندنفره) قرار دهید. این اولین چیزی است که توجه را جلب می‌کند! برای این کار، یک فایل GIF با نام `demo.gif` در ریشه مخزن خود آپلود کنید.)*

![Gameplay Demo](./demo.gif)

---

### **Architectural Evolution**

This project was developed in three distinct phases, each representing a significant step in architectural maturity. This repository is structured to reflect this evolution through its Git history and releases.

*   **Phase 1: Monolithic Foundation (`v1.0`)**
    *   **Architecture:** A standard desktop Swing application with a monolithic structure. The `GamePanel` class handles Model, View, and Controller responsibilities.
    *   **Goal:** Rapid prototyping of core game mechanics and UI.
    *   **Code:** View the complete code for this phase in **[Release v1.0-monolith](https://github.com/abolfazlmoslemi/NetworkOpSim-Java-Simulator/releases/tag/v1.0-monolith)**.

*   **Phase 2: Decoupled Core & Design Patterns (`v2.0`)**
    *   **Architecture:** The core simulation logic was refactored into a platform-agnostic `GameEngine`. The **Strategy Pattern** was implemented to manage diverse network system behaviors (`SystemBehavior`).
    *   **Goal:** Enhance extensibility, testability, and prepare the core logic for distributed environments.
    *   **Code:** View the complete code for this phase in **[Release v2.0-decoupled](https://github.com/abolfazlmoslemi/NetworkOpSim-Java-Simulator/releases/tag/v2.0-decoupled)**.

*   **Phase 3: Client-Server & Multiplayer (`v3.0`)**
    *   **Architecture:** The project was modularized into four Maven modules (`core`, `shared`, `client`, `server`). An authoritative server now manages game state, with communication handled via a DTO-based protocol. A dynamic **Proxy Pattern** was implemented for centralized, AOP-like exception handling on the server.
    *   **Goal:** Implement real-time multiplayer functionality, centralized state management, and a scalable backend.
    *   **Code:** The final code is available on the `main` branch. See **[Release v3.0-multiplayer](https://github.com/abolfazlmoslemi/NetworkOpSim-Java-Simulator/releases/tag/v3.0-multiplayer)**.

---

### **Key Features & Architectural Highlights**

*   **Multi-module Maven Architecture:** Enforces a strong separation of concerns between the data model, core logic, client, and server.
*   **Strategy Design Pattern:** Allows for flexible and extensible network system behaviors without modifying core classes.
*   **Proxy Design Pattern (AOP-style):** Provides clean, centralized exception handling on the server, decoupling error management from business logic.
*   **DTO-Based Network Protocol:** Ensures a stable and decoupled API between the client and server.
*   **Real-time Multiplayer Engine:** Features a state machine-driven `GameSession` manager for handling complex game phases like the "Overtime Build Phase".
*   **Offline Mode with DIV:** Supports offline play and includes a replay system (`ReplayRecorder`) for Data Integrity Validation on the server to prevent cheating.

---

### **Architecture Diagrams**

*These diagrams were created using LaTeX and TikZ for the project's technical paper.*

**Modular Architecture Overview:**
*(تصویر دیاگرام ماژول‌ها را اینجا قرار دهید. ابتدا تصویر را در مخزن آپلود کنید و سپس لینکش را جایگزین کنید)*
`![Module Diagram](path/to/your/module-diagram.png)`

**Strategy Pattern for `SystemBehavior`:**
*(تصویر دیاگرام استراتژی را اینجا قرار دهید)*
`![Strategy Pattern Diagram](path/to/your/strategy-diagram.png)`

**Proxy-based Exception Handling Flow:**
*(تصویر دیاگرام پراکسی را اینجا قرار دهید)*
`![Proxy Pattern Diagram](path/to/your/proxy-diagram.png)`


---

### **Tech Stack**

*   **Language:** Java 17
*   **UI Framework:** Java Swing
*   **Build Tool:** Apache Maven
*   **Networking:** Java Sockets (TCP/IP)
*   **Logging:** SLF4J
*   **Database (Server):** SQLite
*   **Documentation:** LaTeX with TikZ

---

### **How to Run**

1.  **Prerequisites:**
    *   JDK 17 or later
    *   Apache Maven 3.8 or later

2.  **Clone and Build:**
    ```bash
    git clone https://github.com/abolfazlmoslemi/NetworkOpSim-Java-Simulator.git
    cd NetworkOpSim-Java-Simulator
    mvn clean install
    ```

3.  **Run the Server:**
    ```bash
    java -jar NetworkOpSim-Server/target/NetworkOpSim-Server-*.jar
    ```

4.  **Run the Client:**
    (Open a new terminal for each client instance)
    ```bash
    java -jar NetworkOpSim-Client/target/NetworkOpSim-Client-*.jar
    ```
