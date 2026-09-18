# language: es
@SCRUM-143 @HU-48 @backend
Característica: Vincular un vehículo con su equipo a bordo

  Como administrador municipal
  quiero registrar qué dispositivo va en qué bus
  para que cambiar el equipo físico sea un trámite de datos y no una migración

  Antecedentes:
    Dado el vehículo "BUS-1" registrado en la flota

  @criterio-1
  Escenario: Un bus lleva un solo equipo activo a la vez
    Dado un equipo "Tableta cabina 1" con credencial vigente
    Cuando el administrador intenta dar de alta otro equipo en el mismo bus
    Entonces la operación se rechaza explicando que ya hay un equipo activo

  @criterio-2
  Escenario: Cada posición se atribuye al bus de su propio equipo
    Dado el vehículo "BUS-2" registrado en la flota
    Y un equipo con credencial vigente en el "BUS-1"
    Y un equipo con credencial vigente en el "BUS-2"
    Cuando cada equipo reporta su posición
    Entonces cada posición queda atribuida a su propio vehículo

  @criterio-2
  Escenario: Un equipo no puede reportar a nombre de otro bus
    Dado el vehículo "BUS-2" registrado en la flota
    Y un equipo con credencial vigente en el "BUS-1"
    Cuando ese equipo reporta una posición diciendo que es del "BUS-2"
    Entonces la posición queda atribuida al "BUS-1", no al que dijo el equipo

  @criterio-3
  Escenario: Dar de baja un equipo y dar de alta otro no pierde el histórico del bus
    Dado un equipo con credencial vigente en el "BUS-1"
    Y ese equipo reportó 2 posiciones
    Cuando el administrador cambia el equipo a bordo del "BUS-1"
    Y el equipo nuevo reporta 1 posición
    Entonces el "BUS-1" conserva sus 3 posiciones
    Y las posiciones viejas siguen atribuidas al equipo que las reportó
    Y el equipo viejo ya no puede reportar
