export function recentMonthKeys(count, referenceDate = new Date()) {
  const size = Math.max(1, Math.floor(Number(count) || 1))
  const cursor = new Date(referenceDate.getFullYear(), referenceDate.getMonth() - size + 1, 1)
  const months = []
  for (let index = 0; index < size; index += 1) {
    months.push(`${cursor.getFullYear()}-${String(cursor.getMonth() + 1).padStart(2, '0')}`)
    cursor.setMonth(cursor.getMonth() + 1)
  }
  return months
}
