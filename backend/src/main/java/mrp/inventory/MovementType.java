package mrp.inventory;

/**
 * Giá trị của {@code stock_movements.movement_type}. Sổ kho là append-only: mọi sửa tồn
 * phải ghi bằng một dòng ADJUST_IN / ADJUST_OUT mới, không update / delete dòng cũ.
 */
public enum MovementType { IN, OUT, ADJUST_IN, ADJUST_OUT }
