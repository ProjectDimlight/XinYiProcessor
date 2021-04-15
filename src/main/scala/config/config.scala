package config

/**
 * @module config
 * @author ziyue
 * @usecase as global configuration file for scala
 */
object config {
  final val DEBUG: Boolean = true

  final val XLEN: Int = 32
  final val STORE_BUFFER_DEPTH: Int = 32
  final val LOAD_BUFFER_DEPTH: Int = 32

  final val PHY_ADDR_W      = 32
  final val LGC_ADDR_W      = 32
  final val DATA_W          = 32
  final val QUEUE_LEN       = 8
  
  final val REG_ID_W      = 5
  
  final val BOOT_ADDR      = 0x80000000L

  final val BC_NUM          = 4
  final val BC_LINE_SIZE    = 2
  final val BC_LINE_SIZE_W  = 2

  final val FETCH_NUM       = 2
  final val ISSUE_NUM       = 2
  final val ISSUE_NUM_W     = 3
  
  final val N_A_PATH_ID     = 0

  final val ALU_PATH_NUM    = 2
  final val ALU_PATH_ID     = 1
  final val ALU_PATH_NUM_W  = 2

  final val BJU_PATH_NUM    = 0
  final val BJU_PATH_ID     = 2
  
  final val LSU_PATH_NUM    = 2
  final val LSU_PATH_ID     = 3

  final val QUEUE_LEN_w     = 4
  final val PATH_W          = 2
  
  final val L1_W            = 64
  final val L2_W            = 64
}
