// « @Prénom » dans un commentaire (Kévin, 19/11): the comment keeps the members it names
// (mentionIds). Each of them is told once, directly, « Camille t'a nommé dans un commentaire »,
// instead of the usual « a commenté ». Never the one who wrote it, nor a child without the app.

/** The members named by a comment, among [withApp], without its author, each once, sorted. */
export function namedIn(comment: { authorId: string; mentionIds?: unknown }, withApp: Set<string>): string[] {
  const ids = Array.isArray(comment.mentionIds) ? comment.mentionIds : [];
  const named = ids.filter((id): id is string => typeof id === 'string' && id !== comment.authorId && withApp.has(id));
  return [...new Set(named)].sort();
}

/** « Camille t'a nommé dans un commentaire ». */
export function mentionTitle(authorName: string): string {
  return `${authorName} t'a nommé dans un commentaire`;
}
