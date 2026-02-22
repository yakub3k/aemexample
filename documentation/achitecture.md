## AEM Architecture

### Structure

 - Java - class path
 - Apache Felix - OSGi Java Container
 - Apache Sling - FrontEnd
 - AEM - Application Layer

 ### OSGi

  - OS -> JVM -> Module Management -> Life Cycle -> Service Registry

 ### JCR   - Jackrabbit Oak

 ## Oak JCR <- Oak Core <- [Mongo, TarMK (Tar MicroKernel), RDBMK]

  - mutable: /conf, /content, /var, /home, /etc, /system, /tmp
  - immutable: /libs, /apps