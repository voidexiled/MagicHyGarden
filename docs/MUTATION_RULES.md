MGHG Mutation Rules (Guia)
==========================

Resumen rapido
-------------
El sistema de mutaciones esta separado en 3 "slots" independientes:

1) CLIMATE (clima): NONE | RAIN | SNOW | FROZEN
2) LUNAR (lunar): NONE | DAWNLIT | DAWNBOUND | AMBERLIT | AMBERBOUND
3) RARITY (rareza): NONE | GOLD | RAINBOW

Cada slot se evalua por reglas. En cada tick:
- Se filtran reglas que aplican (evento + requisitos).
- Por slot se toma la prioridad mas alta.
- Si hay varias reglas con la misma prioridad, se elige por Weight.
- Se hace el roll de Chance.
- Se consume el cooldown del slot aunque falle el roll.

Archivo de reglas (Asset Editor)
--------------------------------
Ubicacion (asset):
`Server/Farming/Mutations/`

Archivo por defecto:
`Mghg_Mutations.json` (asset id: `Mghg_Mutations`)

Notas:
- El nombre del archivo (sin `.json`) es el **id** del ruleset.
- Se puede editar desde el Asset Editor (ver tooltips al hacer hover).
- Mantener `src/main/resources` en sync si quieres que el cambio quede en source.

Formato base:
{
  "CooldownClock": "RealTime",
  "Rules": [
    { ...regla... }
  ]
}

Campos de regla (MghgMutationRule)
----------------------------------
- Id (string, opcional)
  Identificador logico de la regla.

- EventType (enum, opcional)
  WEATHER | PET | MANUAL | ANY
  Default: WEATHER

- WeatherIds (array<string>, opcional)
  Lista de IDs de weather assets. Solo aplica cuando EventType=WEATHER.
  Si se omite o esta vacio, la regla aplica a cualquier weather.
  En el Asset Editor aparece un selector de Weather.

- Slot (enum, opcional)
  CLIMATE | LUNAR | RARITY
  Default: CLIMATE

- Set (string, requerido)
  Valor de mutacion a aplicar. Debe coincidir con el enum del slot.
  Ej: "RAIN", "FROZEN", "DAWNLIT", "GOLD".
  En el Asset Editor se muestra como dropdown con todas las mutaciones.
  Nota: el sistema valida Slot+Set de forma estricta. Si no coincide, el asset no carga.

- Chance (double, opcional)
  Probabilidad entre 0.0 y 1.0. Default: 0.0

- Priority (int, opcional)
  Prioridad por slot. Regla con mayor prioridad gana.

- Weight (int, opcional)
  Peso para desempatar cuando hay varias reglas con la misma prioridad.
  Default: 1

- CooldownSeconds (int, opcional)
  Cooldown por slot. Si se omite, usa MutationRollCooldownSeconds (Size.json).

- SkipInitialCooldown (bool, opcional)
  Si true, permite que la regla haga el primer roll inmediatamente aun cuando exista cooldown.
  Default: false (el primer tick elegible solo arma el cooldown y no muta).

- RequiresMature (bool, opcional)
  Si true, solo aplica a crops maduros (harvestables).
  Nota: si el crop no tiene FarmingBlock, se considera maduro si su blockId contiene "_stagefinal".

- RequiresPlayerOnline (bool, opcional)
  Si true, solo aplica si el jugador esta online.

- IgnoreSkyCheck (bool, opcional)
  Si true, la regla ignora el bloque encima del crop al resolver el weather.
  Esto permite aplicar reglas incluso bajo techo. Default: false.

- RequiresLight (object, opcional)
  Condiciones de luz (sky / block / RGB). Formato:
  {
    "Match": "ALL|ANY",
    "SkyMin": 0, "SkyMax": 15,
    "BlockMin": 0, "BlockMax": 255,
    "BlockIntensityMin": 0, "BlockIntensityMax": 15,
    "RedMin": 0, "RedMax": 15,
    "GreenMin": 0, "GreenMax": 15,
    "BlueMin": 0, "BlueMax": 15
  }
  - Match controla si todas las condiciones deben cumplirse (ALL) o basta con una (ANY).
  - Si no se define ningun campo, la condicion siempre pasa.

- RequiresTime (object, opcional)
  Condiciones de tiempo (hora / luz solar). Formato:
  {
    "HourMin": 6,
    "HourMax": 18,
    "SunlightMin": 0.25,
    "SunlightMax": 1.0
  }
  - HourMin/HourMax usan la hora del WorldTimeResource (0-23).
  - Si HourMax < HourMin, el rango cruza medianoche.

- MinY / MaxY (int, opcional)
  Restringe la regla a un rango de altura (Y world).

- RequiresStageSets (array<string>, opcional)
  Requiere que el crop este en un StageSet especifico (ej: "Default", "MGHG").

- MinStageIndex / MaxStageIndex (int, opcional)
  Restringe por indice de stage. Útil para reglas que aplican solo en etapas intermedias.

- RequiresSoilBlockIds (array<string>, opcional)
  Requiere que el bloque debajo del crop coincida con alguno de estos ids.

- MustHaveClimate / MustNotHaveClimate (array<string>, opcional)
- MustHaveLunar / MustNotHaveLunar (array<string>, opcional)
- MustHaveRarity / MustNotHaveRarity (array<string>, opcional)
  Listas de valores que el crop debe (o no debe) tener para que la regla aplique.
  Usar nombres exactos de enums (ej: NONE, RAIN, SNOW, FROZEN).
  En el Asset Editor se muestran como dropdowns por slot.

- RequiresAdjacentBlockIds (array<string>, legacy)
  Legacy simple adjacency (6 directions). Sigue funcionando, pero esta oculto en el Asset Editor.
  Usa RequiresAdjacentItems para configuraciones avanzadas (radio/offset/AND/OR).
  (Si usas legacy, AdjacentBlockMatch controla OR/AND; tambien esta oculto.)
  Si RequiresAdjacentItems esta presente, el legacy se ignora.

- RequiresAdjacentItems (array<object>, opcional)
  Requisitos de proximidad con radio/offset. Cada entrada usa:
  - Ids: array<string> (item ids o block ids)
  - Radius: int (radio para X/Y/Z)
  - RadiusX / RadiusY / RadiusZ: int (override por eje)
  - OffsetX / OffsetY / OffsetZ: int (offset desde la posicion del crop)
  - MinCount / MaxCount: int (conteo de matches dentro del radio)
  Nota: el area escaneada es una caja axis-aligned de (2*R + 1) por eje.
  Ejemplo rapido:
  "RequiresAdjacentItems": [
    { "Ids": ["Plant_Crop_Lettuce_Block"], "Radius": 5, "OffsetY": 1 }
  ]
  Nota: si necesitas radio distinto por item, crea otra entrada.
  En el Asset Editor, Ids muestra selector de Item; tambien puedes escribir ids de blocks o states.

- AdjacentItemsMatch (enum, opcional)
  ANY | ALL
  Controla si RequiresAdjacentItems se evalua como OR (ANY) o AND (ALL). Default: ANY.

- RequiresAdjacentParticles (array<object>, opcional)
  Similar a RequiresAdjacentItems, pero basado en particulas. Soporta dos fuentes:
  - Runtime (paquetes enviados al cliente) -> detecta **todas** las particulas visibles
  - Block assets -> BlockParticleSetId / ModelParticle.SystemId
  Campos disponibles:
  - AnyParticle (bool): si true, cualquier particula cuenta (ignora ids).
  - UseRuntimeParticles (bool): usa el tracker de paquetes (default true).
  - UseBlockParticles (bool): usa particulas definidas en BlockType (default true).
  - ParticleSystemIds: array<string>
  - BlockParticleSetIds: array<string>
  - Radius / RadiusX / RadiusY / RadiusZ
  - OffsetX / OffsetY / OffsetZ
  - MinCount / MaxCount
  - MaxAgeSeconds: ventana de tiempo para runtime (default 2s, max 30s)
  Nota:
  - Runtime detecta particulas **enviadas a jugadores**. Si no hay jugadores cerca,
    no hay paquetes, por lo tanto no hay match.
  - El tracker puede ver el mismo efecto varias veces (una por jugador) porque los
    paquetes se mandan por player. Para reglas con MinCount/MaxCount, usa una
    ventana corta (MaxAgeSeconds) si quieres evitar conteos inflados.
  - Para BlockParticles, los ids se comparan con BlockParticleSetId y ModelParticle.SystemId.
  - Los paquetes de block particles generan ids extra:
    block:<BlockId>, blockevent:<Event>, blockevent:<BlockId>:<Event>
    (ej: block:stone, blockevent:break, blockevent:stone:break)

- AdjacentParticlesMatch (enum, opcional)
  ANY | ALL
  Controla si RequiresAdjacentParticles se evalua como OR (ANY) o AND (ALL). Default: ANY.

Ejemplo de regla con luz y particulas
-------------------------------------
{
  "Id": "mushroom_glow",
  "EventType": "ANY",
  "Slot": "CLIMATE",
  "Set": "RAIN",
  "Chance": 1,
  "RequiresLight": {
    "Match": "ALL",
    "SkyMax": 3,
    "RedMin": 6
  },
  "RequiresAdjacentParticles": [
    {
      "ParticleSystemIds": ["Zombie_Mist"],
      "Radius": 4
    }
  ]
}

Como funciona la resolucion de weather
--------------------------------------
El motor toma el weather actual via `MghgWeatherResolver.resolveWeatherId(...)`:
- Respeta forced weather.
- Respeta sky-check (si hay un bloque encima, no aplica clima).

Luego las reglas comparan el weatherId contra WeatherIds (si existen).
Si un WeatherId es invalido/no existe, simplemente nunca matchea.

Reglas para evitar sobreescrituras
----------------------------------
El sistema no hace "downgrade" automatico. Para evitar sobreescrituras:
- Usa MustNotHaveClimate / MustHaveClimate.
Ejemplo:
- Rain aplica RAIN solo si el crop esta en NONE.
- Snow aplica SNOW solo si el crop esta en NONE.
- Rain + SNOW -> FROZEN (con prioridad mayor).

Stacking (climate + lunar + rarity)
-----------------------------------
El modelo permite los 3 slots simultaneos.
Las visuales dependen de tener states definidos para la combinacion.

Ej:
Para `RAIN + DAWNLIT` necesitas:
  mghg_rain_dawnlit_stage1/2/final
  stage set: mghg_rain_dawnlit
  item state: mghg_rain_dawnlit

En este repo ya se agregaron combinaciones dummy (copias) para testear
climate + lunar + rarity. Cuando confirmes que el sistema funciona, puedes
eliminar las combinaciones que no uses y reemplazarlas por assets reales.

Como agregar nuevos weathers
----------------------------
1) Identifica el WeatherId (asset id real).
2) Agrega reglas en `Mghg_Mutations.json` (asset) usando WeatherIds.
3) Asegura estados/visuales si ese weather aplica nuevas mutaciones.
4) Usa `/crop reload mutations` para recargar reglas sin reiniciar.

Hot reload
----------
- `/crop reload mutations` recarga solo reglas.
- `/crop reload growth` recarga Size.json (chances y drops).
- `/crop reload all` recarga reglas + size + crop registry.

Cooldown Clock
--------------
El campo `CooldownClock` define como se interpretan los segundos del cooldown:
- RealTime: usa el reloj real del servidor (recomendado).
- GameTime: usa el tiempo de mundo (puede ir mas rapido/lento segun configuracion).

Fallback
--------
Si el asset no existe o esta vacio, el sistema usa el fallback del GrowthModifier
(MutationChance* + Rain/Snow/Frozen weathers). Esto mantiene compatibilidad basica.

Como agregar nuevas mutaciones
------------------------------
1) Agrega el nuevo valor al enum correspondiente:
   - ClimateMutation, LunarMutation o RarityMutation.
2) Actualiza `MghgCropVisualStateResolver` para mapear el nuevo valor a un sufijo.
3) Crea states en el Block asset:
   - mghg_<variant>_stage1/2/final
4) Agrega el stage set en `Farming.Stages`:
   - mghg_<variant> -> [default, stage1, stage2, stagefinal]
5) Agrega el item state en el item asset:
   - mghg_<variant>
6) Crea reglas en `Mghg_Mutations.json`.

Ejemplo de reglas (Rain/Snow/Frozen + Lunar + Rarity)
----------------------------------------------------
Ver `Server/Farming/Mutations/Mghg_Mutations.json` para
los ejemplos de prueba que usan weather ids:
- Zone2_Thunder_Storm / Zone4_Storm -> RAIN
- Zone1_Cloudy_Medium -> SNOW
- Skylands_Light -> DAWNLIT / DAWNBOUND
- Zone_2_Sand_Storm -> AMBERLIT / AMBERBOUND
- Zone1_Swamp_Foggy -> GOLD
- Zon3_Cave_Volcanic -> RAINBOW

Notas:
- Ajusta Chance y CooldownSeconds segun balance.
- Si un WeatherId no existe, la regla no se disparara.
