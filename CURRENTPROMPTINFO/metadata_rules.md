# Metadata de Clases y Métodos Usados en Reglas (.drl)

Este documento resume las clases de dominio realmente usadas en las reglas, sus métodos y atributos consultados, y las dependencias inferidas solo por firmas de métodos, según el plan SSOT.

---

## FoundLipid

| Método              | Retorno inferido     | Parámetros visibles | Rol breve                                      |
|---------------------|----------------------|---------------------|------------------------------------------------|
| setScore            | void                 | double              | Asigna score a un lípido                       |
| setDescrCorrect     | void                 | String              | Marca descripción como correcta                 |
| setDescrIncorrect   | void                 | String              | Marca descripción como incorrecta               |
| getAppliedPresence  | boolean              | —                   | Consulta si se aplicó presencia                |
| setAppliedPresence  | void                 | boolean             | Marca si se aplicó presencia                   |
| getAppliedIntensity | boolean              | —                   | Consulta si se aplicó intensidad               |
| setAppliedIntensity | void                 | boolean             | Marca si se aplicó intensidad                  |
| getListAdducts      | List<FoundAdduct>    | —                   | Devuelve lista de aductos asociados             |

**Atributos consultados:**
- score
- descrCorrect
- descrIncorrect
- appliedPresence
- appliedIntensity

**Dependencias:**
- FoundAdduct (por retorno de getListAdducts)

---

## FoundAdduct

| Método      | Retorno inferido | Parámetros visibles | Rol breve                        |
|-------------|------------------|---------------------|-----------------------------------|
| getIntensity| double           | —                   | Devuelve intensidad del aducto    |

**Atributos consultados:**
- adductName

**Dependencias:**
- Ninguna

---

## MobilePhases (enum)

| Constante   | Rol breve                                 |
|-------------|-------------------------------------------|
| NH4         | Fase móvil amonio                         |
| CH3CN       | Fase móvil acetonitrilo                   |
| CH3OH       | Fase móvil metanol                        |
| CH3COO      | Fase móvil acetato                        |
| HCOO        | Fase móvil formiato                       |

**Dependencias:**
- Ninguna

---

**Notas:**
- Solo se incluyen clases con uso real en reglas.
- Las dependencias solo reflejan tipos en firmas de métodos, no co-ocurrencia.
- Se excluyen tipos estándar (String, List, boolean, double).
- El detalle de métodos y atributos se infiere de los patrones y llamadas en LHS/RHS.
