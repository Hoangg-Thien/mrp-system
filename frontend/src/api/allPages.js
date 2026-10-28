export async function fetchAllPages(fetchPage) {
  const content = []
  let page = 0
  let result
  do {
    result = await fetchPage(page, 100)
    content.push(...result.content)
    page += 1
  } while (page < result.totalPages)
  return { ...result, content }
}
