package net.barribob.maelstrom.mob

import net.barribob.maelstrom.general.yOffset
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.boss.dragon.EnderDragonPart
import net.minecraft.entity.damage.DamageSource
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import kotlin.math.min
import kotlin.math.pow

/**
 * Static utility functions that use or depend [Entity]
 */
object MobUtils {
    fun leapTowards(entity: LivingEntity, target: Vec3d, horzVel: Double, yVel: Double) {
        val dir = target.subtract(entity.pos).normalize()
        val leap: Vec3d = Vec3d(dir.x, 0.0, dir.z).normalize().multiply(horzVel).yOffset(yVel)
        val clampedYVelocity = if (entity.velocity.y < 0.1) leap.y else 0.0

        // Normalize to make sure the velocity doesn't go beyond what we expect
        var horzVelocity = entity.velocity.add(leap.x, 0.0, leap.z)
        val scale = horzVel / horzVelocity.length()
        if (scale < 1) {
            horzVelocity = horzVelocity.multiply(scale)
        }

        entity.velocity = horzVelocity.yOffset(clampedYVelocity)
    }

    fun handleAreaImpact(radius: Double, maxDamage: Float, source: LivingEntity, pos: Vec3d, damageSource: DamageSource,
                         knockbackFactor: Double = 1.0, fireFactor: Int = 0, damageDecay: Boolean = true, effectCallback: (Entity, Double) -> Unit = { _, _ -> run {} }) {

        val list: List<Entity> = source.world.getEntities(source, Box(BlockPos(pos)).expand(radius))
        val isInstance = { i: Entity -> i is LivingEntity || i is EnderDragonPart || i.collides() }
        val radiusSq = radius.pow(2.0)

        list.stream().filter(isInstance).forEach { entity: Entity ->
            // Get the hitbox size of the entity because otherwise explosions are less
            // effective against larger mobs
            val avgEntitySize: Double = entity.boundingBox.averageSideLength * 0.75

            // Choose the closest distance from the center or the head to encourage
            // headshots
            val distanceToCenter = entity.boundingBox.center.distanceTo(pos)
            val distanceToHead = entity.getCameraPosVec(1.0F).distanceTo(pos)
            val distanceToFeet = entity.pos.distanceTo(pos)
            val distance = min(distanceToCenter, min(distanceToHead, distanceToFeet))

            // Subtracting the average size makes it so that the full damage can be dealt
            // with a direct hit
            val adjustedDistance = (distance - avgEntitySize).coerceAtLeast(0.0)
            val adjustedDistanceSq = adjustedDistance.pow(2.0)
            val damageFactor: Double = if (damageDecay) ((radiusSq - adjustedDistanceSq) / radiusSq).coerceIn(0.0, 1.0) else 1.0

            // Damage decays by the square to make missed impacts less powerful
            val damageFactorSq = damageFactor.pow(2.0)
            val damage: Double = maxDamage * damageFactorSq
            if (damage > 0 && adjustedDistanceSq < radiusSq) {
                entity.setOnFireFor((fireFactor * damageFactorSq).toInt())
                entity.damage(damageSource, damage.toFloat())
                val entitySizeFactor: Double = if (avgEntitySize == 0.0) 1.0 else (1.0 / avgEntitySize).coerceIn(0.5, 1.0)
                val entitySizeFactorSq = entitySizeFactor.pow(2.0)

                // Velocity depends on the entity's size and the damage dealt squared
                val velocity: Vec3d = entity.boundingBox.center.subtract(pos).normalize().multiply(damageFactorSq).multiply(knockbackFactor).multiply(entitySizeFactorSq)
                entity.addVelocity(velocity.x, velocity.y, velocity.z)
                effectCallback(entity, damageFactorSq)
            }
        }
    }
}