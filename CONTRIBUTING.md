# Guía de Contribución

Guía para compilar y contribuir cambios a **NeaFutbol** (Paper/Spigot 1.21).

---

## Entorno de Desarrollo

1. **Requisitos:**
   - Java 17 o Java 21 JDK.
   - Maven 3.8+.

2. **Compilación local:**
   ```bash
   mvn clean package
   ```
   El archivo JAR resultante se generará en la carpeta `target/`.

---

## Estándares de Código

- Respeta la arquitectura basada en listeners, managers y utilidades asíncronas para cálculos de físicas.
- Mantén el rendimiento: evita escaneos de entidades en el hilo principal (`Main Thread`).
- Realiza commits descriptivos usando [Conventional Commits](https://www.conventionalcommits.org/):
  - `feat: ...`
  - `fix: ...`
  - `docs: ...`
  - `refactor: ...`
