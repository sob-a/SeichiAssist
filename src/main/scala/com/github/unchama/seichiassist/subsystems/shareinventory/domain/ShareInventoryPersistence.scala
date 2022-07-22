package com.github.unchama.seichiassist.subsystems.shareinventory.domain

import com.github.unchama.seichiassist.subsystems.shareinventory.domain.bukkit.InventoryContents

import java.util.UUID

trait ShareInventoryPersistence[F[_]] {

  /**
   * [[InventoryContents]]をセーブします。
   * @param inventoryContents セーブ対象の[[InventoryContents]]
   */
  def save(targetUuid: UUID, inventoryContents: InventoryContents): F[Unit]

  /**
   * セーブされている[[InventoryContents]]をロードします。
   */
  def load(targetUuid: UUID): F[Option[InventoryContents]]

  /**
   * セーブされている[[InventoryContents]]を完全に削除します。
   */
  def clear(targetUuid: UUID): F[Unit]

}
