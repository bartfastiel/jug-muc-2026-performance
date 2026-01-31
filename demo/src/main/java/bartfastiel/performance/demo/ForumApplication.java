package bartfastiel.performance.demo;

import jakarta.persistence.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

@SpringBootApplication
public class ForumApplication {

    static final long SEED = 42L;

    public static void main(String[] args) {
        var url = System.getenv("DB_URL");
        var user = System.getenv("DB_USER");
        var password = System.getenv("DB_PASSWORD");

        var validationErrors = new ArrayList<String>();
        if (url == null || url.isBlank()) {
            validationErrors.add("Environment variable DB_URL is not set.");
        }
        if (user == null || user.isBlank()) {
            validationErrors.add("Environment variable DB_USER is not set.");
        }
        if (password == null || password.isBlank()) {
            validationErrors.add("Environment variable DB_PASSWORD is not set.");
        }
        if (!validationErrors.isEmpty()) {
            validationErrors.forEach(System.err::println);
            System.exit(1);
        }

        System.setProperty("spring.datasource.url", url);
        System.setProperty("spring.datasource.username", user);
        System.setProperty("spring.datasource.password", password);

        SpringApplication.run(ForumApplication.class, args);
    }

    @Bean
    CommandLineRunner init(ForumCategoryRepository repo) {
        return args -> {
            if (0 < repo.count()) {
                return;
            }
            IO.println("Seeding database with sample data...");

            var rnd = new Random(SEED);

            for (var name : Categories.NAMES) {
                var category = new ForumCategory(name);

                var threads = 6 + rnd.nextInt(3); // 6–8 Threads
                for (var i = 0; i < threads; i++) {
                    var thread = new ForumThread(RandomData.threadTitle(rnd));
                    category.addThread(thread);

                    var posts = 10 + rnd.nextInt(6); // 10–15 Posts
                    for (var p = 0; p < posts; p++) {
                        var post = new ForumPost(RandomData.user(rnd));
                        thread.addPost(post);

                        var emojis = 5 + rnd.nextInt(4); // 5–8 Emojis
                        for (var e = 0; e < emojis; e++) {
                            post.addEmoji(new EmojiReaction(RandomData.emoji(rnd)));
                        }
                    }
                }

                repo.save(category);
                IO.println("Created category: " + name);
            }
            IO.println("Database seeding complete.");
        };
    }
}

@Controller
class ForumController {

    private final ForumCategoryRepository repo;

    ForumController(ForumCategoryRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/")
    String forum(Model model) {
        var result = new ArrayList<CategoryView>();

        for (var category : repo.findAll()) {
            var threads =
                    category.threads.stream()
                            .map(t -> new ThreadView(
                                    t.title,
                                    t.posts.stream()
                                            .mapToInt(p -> p.emojis.size())
                                            .sum()
                            ))
                            .sorted(Comparator.comparingInt(ThreadView::emojiCount).reversed())
                            .limit(5)
                            .toList();

            result.add(new CategoryView(category.name, threads));
        }

        model.addAttribute("categories", result);
        return "forum";
    }
}

@Entity
class ForumCategory {

    @Id
    @GeneratedValue
    Long id;

    String name;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL)
    List<ForumThread> threads = new ArrayList<>();

    public ForumCategory() {}

    ForumCategory(String name) {
        this.name = name;
    }

    void addThread(ForumThread t) {
        t.category = this;
        threads.add(t);
    }
}

@Entity
class ForumThread {

    @Id
    @GeneratedValue
    Long id;

    String title;

    @ManyToOne(fetch = FetchType.LAZY)
    ForumCategory category;

    @OneToMany(mappedBy = "thread", cascade = CascadeType.ALL)
    List<ForumPost> posts = new ArrayList<>();

    public ForumThread() {}

    ForumThread(String title) {
        this.title = title;
    }

    void addPost(ForumPost p) {
        p.thread = this;
        posts.add(p);
    }
}

@Entity
class ForumPost {

    @Id
    @GeneratedValue
    Long id;

    String author;

    @ManyToOne(fetch = FetchType.LAZY)
    ForumThread thread;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
    List<EmojiReaction> emojis = new ArrayList<>();

    public ForumPost() {}

    ForumPost(String author) {
        this.author = author;
    }

    void addEmoji(EmojiReaction e) {
        e.post = this;
        emojis.add(e);
    }
}

@Entity
class EmojiReaction {

    @Id
    @GeneratedValue
    Long id;

    String emoji;

    @ManyToOne(fetch = FetchType.LAZY)
    ForumPost post;

    public EmojiReaction() {}

    EmojiReaction(String emoji) {
        this.emoji = emoji;
    }
}

interface ForumCategoryRepository extends JpaRepository<ForumCategory, Long> {}

record CategoryView(String name, List<ThreadView> threads) {}
record ThreadView(String title, int emojiCount) {}

final class Categories {
    static final List<String> NAMES = List.of(
            "Windows vs. macOS vs. Linux",
            "Ist das Blau wirklich richtig?",
            "Story Points ≠ Tage",
            "Dark Mode kommt nächste Woche™",
            "KI + Blockchain = Erfolg?",
            "Maven vs. Gradle",
            "Works on my Machine™",
            "Kaffee ist keine Lösung – doch"
    );
}

final class RandomData {

    static String threadTitle(Random r) {
        var a = List.of("Hot Take", "Unbeliebte Meinung", "Endlich geklärt?", "Schon wieder");
        var b = List.of(
                "im Enterprise",
                "nach dem Refactoring",
                "vor dem ersten Kaffee",
                "laut Projektleitung"
        );
        return a.get(r.nextInt(a.size())) + " – " + b.get(r.nextInt(b.size()));
    }

    static String user(Random r) {
        var u = List.of("alex", "sam", "chris", "pat", "lea", "max");
        return u.get(r.nextInt(u.size()));
    }

    static String emoji(Random r) {
        var e = List.of("👍", "😂", "🤦", "☕", "🔥", "😅");
        return e.get(r.nextInt(e.size()));
    }
}
