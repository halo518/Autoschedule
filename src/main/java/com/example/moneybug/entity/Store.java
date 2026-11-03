package com.example.moneybug.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
public class Store {

    public enum Scale { UNDER_5, OVER_5 }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Scale scale;

    @Column(name = "minimum_wage", nullable = false)
    private Integer minimumWage;
}
