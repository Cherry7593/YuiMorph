package moe.yui.morph

import moe.yui.morph.disguise.DisguiseId
import org.bukkit.permissions.Permissible

/** 权限节点。默认值在 paper-plugin.yml 中声明。 */
object Permissions {
    const val MORPH = "yuimorph.morph"
    const val UNMORPH = "yuimorph.unmorph"
    const val ACQUIRE = "yuimorph.acquire"
    /** 不需要解锁就能使用任意伪装。 */
    const val MORPH_ANY = "yuimorph.morph.any"
    const val ADMIN = "yuimorph.admin"
    /** 使用 /morphprop。部分属性还需要额外权限（见 PropertyDefs）。 */
    const val PROPERTY = "yuimorph.property"
    const val REQUEST_SEND = "yuimorph.request.send"
    const val REQUEST_ACCEPT = "yuimorph.request.accept"
    const val REQUEST_DENY = "yuimorph.request.deny"

    private const val MORPH_AS = "yuimorph.morph.as."

    /**
     * 单个伪装的权限 `yuimorph.morph.as.<namespace>.<key>`。
     * 没有显式设置时视为允许，管理员可以通过设为 false 来单独禁止某个伪装。
     */
    fun canMorphAs(who: Permissible, id: DisguiseId): Boolean {
        val node = MORPH_AS + id.permissionSuffix
        return !who.isPermissionSet(node) || who.hasPermission(node)
    }
}
