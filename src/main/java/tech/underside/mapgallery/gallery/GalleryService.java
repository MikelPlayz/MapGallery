package tech.underside.mapgallery.gallery;

import tech.underside.mapgallery.model.GalleryItem;
import tech.underside.mapgallery.storage.DataRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class GalleryService {
    private final DataRepository repo;

    public GalleryService(DataRepository repo) {
        this.repo = repo;
    }

    public List<GalleryItem> getAll() {
        return repo.allGallery().stream().sorted(Comparator.comparingInt(GalleryItem::getId)).toList();
    }

    public List<GalleryItem> search(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return getAll().stream().filter(i -> i.getDisplayName().toLowerCase(Locale.ROOT).contains(q)
                || i.getCreatorName().toLowerCase(Locale.ROOT).contains(q)
                || i.getTitle().toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    public Optional<GalleryItem> byId(int id) {
        return repo.byId(id);
    }

    public boolean remove(int id) {
        return repo.removeGallery(id);
    }
}
