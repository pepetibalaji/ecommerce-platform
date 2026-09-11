/** Search only the supplied page; never imply a server-wide search. */
export function filterWorkspaceRecords<T>(records: T[], query: string, fields: (record: T) => unknown[]): T[] {
  const term = query.trim().toLocaleLowerCase();
  return term ? records.filter(record => fields(record).flat().filter(value => value != null).join(" ").toLocaleLowerCase().includes(term)) : records;
}
