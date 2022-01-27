# shrinking-kotlin
减少 Kotlin 编译后的文件体积

```kts
plugins {
    id("org.tabooproject.shrinkingkt") version "<version>"
}

shrinking {
    annotation = "org.tabooproject.reflex.Internal"
}
```

```kotlin
@Internal
class AsmClassVisitor
```

将会在编译时移除被 @Internal 标注的文件的 @Metadata 注解，以及所有文件的 `Source Debug` 信息