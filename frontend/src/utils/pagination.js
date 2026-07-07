export async function fetchAllRecords(pageApi, params = {}, size = 100) {
  const records = []
  let current = 1
  let total = 0

  do {
    const page = await pageApi({ ...params, current, size })
    const rows = page.records || []
    records.push(...rows)
    total = Number(page.total || rows.length)
    if (rows.length === 0) break
    current += 1
  } while (records.length < total)

  return records
}
