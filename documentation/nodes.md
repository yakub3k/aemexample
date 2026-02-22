# Repository Content Classification

## JCR - Tree structure database

### Public (jcr:mixinTypes = granite:PublicArea)
 - overlaid (/libs -> /apps)
 - inherited (sling:resourceSuperType)
 - used directly (sling:resourceType)

### Final (jcr:mixins = granite:FinalArea)

 - cannot be overlaid or inherited
 - used directly only (sling:resourceType)
 - children nodes are internal by default

### Internal (jcr:mixinTypes = granite:internalArea)

 - cannot be overlaid, inherited, used directly
 - internal AEM functionalities
 - grayed out in CRX


### Abstract (jcr:mixinTypes = graniteAbstractArea)

 - overlaid
 - inherited (sling:resourceSuperType)
 - not directly